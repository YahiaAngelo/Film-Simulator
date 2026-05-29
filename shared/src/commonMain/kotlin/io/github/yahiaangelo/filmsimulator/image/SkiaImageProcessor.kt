package io.github.yahiaangelo.filmsimulator.image

import io.github.yahiaangelo.filmsimulator.image.shaders.ImageProcessingShader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val DEFAULT_PREVIEW_MAX_DIMENSION = 960
private const val LEGACY_THUMBNAIL_WIDTH = 320

/**
 * Cross-platform image processor built on Skia (skiko). Owns the whole pipeline:
 * decoded source → LUT → adjustments → encoded output. Used for live preview
 * (downscaled), film-LUT thumbnails (tiny) and full-resolution export through a
 * single code path so what the user sees on screen matches what gets exported.
 *
 * The processor is stateful only as a cheap memoization layer: the most recently
 * decoded source [Image] and the most recently packed LUT [Image] are kept so
 * dragging a slider doesn't redo `Image.makeFromEncoded` or LUT packing on every
 * frame. Both caches are cleared automatically when the inputs change.
 */
class SkiaImageProcessor {

    private data class Lut3D(val size: Int, val data: FloatArray) {
        override fun equals(other: Any?): Boolean =
            other is Lut3D && other.size == size && other.data.contentEquals(data)
        override fun hashCode(): Int = 31 * size + data.contentHashCode()
    }

    private var cachedSourceKey: Int = 0
    private var cachedSource: Image? = null
    private var cachedLutKey: Int = 0
    private var cachedLutImage: Image? = null
    private var cachedLutSize: Int = 0

    /**
     * Run the pipeline.
     *
     * @param imageBytes encoded source image (any format Skia can decode).
     * @param lutBytes optional .cube file bytes. Null bypasses the LUT entirely.
     * @param adjustments slider values; defaults to a no-op.
     * @param maxDimension if set, the largest output dimension is clamped to this
     *   value preserving aspect ratio (used for previews and thumbnails).
     * @param quality JPEG quality 0..100 for the encoded result.
     * @param grainSeed value mixed into the per-pixel grain hash so successive
     *   previews can stay stable while exports can pick a fresh pattern.
     * @param highQualityGrain when true, the shader switches the grain block
     *   to a film-emulation path (multi-octave FBM with per-channel cell
     *   sizes, density-weighted soft-light blend). ~30-40x more expensive than
     *   the cheap path, so enable for export only — preview should leave it
     *   false to keep slider interaction smooth.
     * @param onProgress when non-null, the render is split into tiles and this
     *   callback is invoked with a 0..1 progress value after each tile. Each
     *   tile also yields the worker coroutine so the main thread can render
     *   the loading dialog's progress bar smoothly. Pass null to do a single
     *   draw call (used for preview/thumbnail paths where a progress bar
     *   would be overkill).
     */
    suspend fun process(
        imageBytes: ByteArray,
        lutBytes: ByteArray?,
        adjustments: ImageAdjustments = ImageAdjustments(),
        maxDimension: Int? = null,
        quality: Int = 95,
        grainSeed: Float = 0f,
        highQualityGrain: Boolean = false,
        onProgress: (suspend (Float) -> Unit)? = null,
    ): ByteArray? = withContext(Dispatchers.Default) {
        val source = sourceImage(imageBytes) ?: return@withContext null
        val lut = lutBytes?.let { bytes ->
            parseCubeLut(bytes.decodeToString()) ?: return@withContext null
        }
        val lutImage = lut?.let { lutImage(it) }

        val (outWidth, outHeight) = computeOutputSize(source.width, source.height, maxDimension)

        val effect = RuntimeEffect.makeForShader(ImageProcessingShader.SHADER)
        val builder = RuntimeShaderBuilder(effect).apply {
            uniform("useLut", if (lutImage != null) 1f else 0f)
            uniform("lutIntensity", (adjustments.lutIntensity / 100f).coerceIn(0f, 2f))
            uniform("lutSize", (lut?.size ?: 1).toFloat())
            uniform(
                "imageScale",
                source.width.toFloat() / outWidth,
                source.height.toFloat() / outHeight,
            )
            uniform("resolution", outWidth.toFloat(), outHeight.toFloat())

            // Normalize the slider-domain values to shader-domain ranges. The UI
            // surfaces -20..20 (or 0..10 for grain/CA) which is a relic of the
            // earlier shader; keep the same feel by dividing similarly.
            uniform("exposure", (adjustments.exposure / 10f).coerceIn(-2f, 2f))
            uniform("contrast", (adjustments.contrast / 40f).coerceIn(-0.5f, 0.5f))
            uniform("shadows", (adjustments.shadows / 160f).coerceIn(-0.125f, 0.125f))
            uniform("highlights", (adjustments.highlights / 40f).coerceIn(-0.5f, 0.5f))
            uniform("saturation", (adjustments.saturation / 20f).coerceIn(-1f, 1f))
            uniform("temperature", (adjustments.temperature / 20f).coerceIn(-1f, 1f))
            uniform("grain", (adjustments.grain / 10f).coerceIn(0f, 1f))
            uniform("grainSeed", grainSeed)
            uniform("grainQuality", if (highQualityGrain) 1f else 0f)
            uniform("chromaticAberration", (adjustments.chromaticAberration / 10f).coerceIn(0f, 1f))

            child(
                "image",
                source.makeShader(
                    tmx = FilterTileMode.CLAMP,
                    tmy = FilterTileMode.CLAMP,
                    sampling = SamplingMode.LINEAR,
                    localMatrix = null,
                ),
            )
            // Always bind a "lut" child — SkSL requires every declared child shader
            // to be set even when we won't sample it. A 1×1 transparent image is
            // enough when useLut == 0.
            child(
                "lut",
                (lutImage ?: dummyLutImage).makeShader(
                    tmx = FilterTileMode.CLAMP,
                    tmy = FilterTileMode.CLAMP,
                    sampling = SamplingMode.DEFAULT,
                    localMatrix = null,
                ),
            )
        }

        val info = ImageInfo(outWidth, outHeight, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        val surface = Surface.makeRaster(info)
        val paint = Paint().apply { shader = builder.makeShader() }

        if (onProgress == null) {
            surface.canvas.drawRect(Rect(0f, 0f, outWidth.toFloat(), outHeight.toFloat()), paint)
        } else {
            // Tile the render so the worker can yield between tiles. The shader
            // is per-pixel (it uses fragCoord directly), so drawing a clipped
            // rect produces the same pixels as one big draw — Skia handles the
            // raster bucketing internally. We aim for ~32 tiles total which
            // gives a smooth progress bar without too much per-tile overhead.
            val targetTiles = 32
            val aspect = outWidth.toFloat() / outHeight.toFloat()
            val tilesX = max(1, sqrt(targetTiles * aspect).roundToInt())
            val tilesY = max(1, (targetTiles.toFloat() / tilesX).roundToInt())
            val tileW = (outWidth + tilesX - 1) / tilesX
            val tileH = (outHeight + tilesY - 1) / tilesY
            val total = (tilesX * tilesY).toFloat()
            var done = 0
            for (ty in 0 until tilesY) {
                for (tx in 0 until tilesX) {
                    val left = tx * tileW
                    val top = ty * tileH
                    val right = minOf(left + tileW, outWidth)
                    val bottom = minOf(top + tileH, outHeight)
                    surface.canvas.drawRect(
                        Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()),
                        paint,
                    )
                    done++
                    onProgress(done / total)
                    // yield() lets the dispatcher schedule other coroutines —
                    // crucially, the ones forwarding UI state changes — so the
                    // progress bar can actually animate while we render.
                    yield()
                }
            }
        }

        val snapshot = surface.makeImageSnapshot()
        snapshot.encodeToData(EncodedImageFormat.JPEG, quality)?.bytes
    }

    /**
     * Backwards-compatible entry point matching the old SkiaLutProcessor API used
     * by thumbnail generation. Defaults to a 320 px wide thumbnail and skips
     * adjustments entirely.
     */
    suspend fun applyLut(
        imageBytes: ByteArray,
        lutBytes: ByteArray,
        createThumbnail: Boolean = false,
        quality: Int = 95,
    ): ByteArray? = process(
        imageBytes = imageBytes,
        lutBytes = lutBytes,
        adjustments = ImageAdjustments(),
        maxDimension = if (createThumbnail) LEGACY_THUMBNAIL_WIDTH else null,
        quality = quality,
    )

    /** Drops any cached source / LUT. Call when navigating away from the editor. */
    fun clearCache() {
        cachedSource = null
        cachedSourceKey = 0
        cachedLutImage = null
        cachedLutKey = 0
        cachedLutSize = 0
    }

    private fun sourceImage(bytes: ByteArray): Image? {
        val key = bytes.contentHashCode()
        cachedSource?.let { if (cachedSourceKey == key) return it }
        val decoded = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
        cachedSource = decoded
        cachedSourceKey = key
        return decoded
    }

    private fun lutImage(lut: Lut3D): Image {
        val key = lut.hashCode()
        cachedLutImage?.let { if (cachedLutKey == key) return it }
        val packed = buildLutImage(lut)
        cachedLutImage = packed
        cachedLutKey = key
        cachedLutSize = lut.size
        return packed
    }

    private fun buildLutImage(lut: Lut3D): Image {
        val size = lut.size
        val width = size
        val height = size * size
        val pixels = ByteArray(width * height * 4)

        for (i in 0 until size * size * size) {
            val src = i * 3
            val dst = i * 4
            pixels[dst]     = (lut.data[src].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
            pixels[dst + 1] = (lut.data[src + 1].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
            pixels[dst + 2] = (lut.data[src + 2].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
            pixels[dst + 3] = 0xFF.toByte()
        }

        val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
        return Image.makeRaster(info, pixels, info.minRowBytes)
    }

    private fun computeOutputSize(srcW: Int, srcH: Int, maxDimension: Int?): Pair<Int, Int> {
        if (maxDimension == null || max(srcW, srcH) <= maxDimension) return srcW to srcH
        val scale = maxDimension.toFloat() / max(srcW, srcH)
        val w = (srcW * scale).toInt().coerceAtLeast(1)
        val h = (srcH * scale).toInt().coerceAtLeast(1)
        return w to h
    }

    private fun parseCubeLut(text: String): Lut3D? {
        var size = 0
        val data = ArrayList<Float>(0)
        val whitespace = Regex("\\s+")

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            if (line.startsWith("LUT_3D_SIZE", ignoreCase = true)) {
                size = line.substringAfter("LUT_3D_SIZE").trim().toIntOrNull() ?: return null
                if (size <= 0 || size > 256) return null
                data.ensureCapacity(size * size * size * 3)
                continue
            }
            if (line.startsWith("TITLE", ignoreCase = true) ||
                line.startsWith("DOMAIN_", ignoreCase = true) ||
                line.startsWith("LUT_1D_", ignoreCase = true)
            ) continue

            if (size == 0) continue
            val parts = line.split(whitespace)
            if (parts.size < 3) continue
            val r = parts[0].toFloatOrNull() ?: continue
            val g = parts[1].toFloatOrNull() ?: continue
            val b = parts[2].toFloatOrNull() ?: continue
            data += r; data += g; data += b
        }

        if (size == 0) return null
        val expected = size * size * size * 3
        if (data.size < expected) return null
        return Lut3D(size, data.subList(0, expected).toFloatArray())
    }

    private val dummyLutImage: Image by lazy {
        // 1×1 black pixel — only ever bound when useLut == 0, in which case the
        // shader never samples it.
        val info = ImageInfo(1, 1, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
        Image.makeRaster(info, byteArrayOf(0, 0, 0, 0xFF.toByte()), info.minRowBytes)
    }

    @Suppress("unused")
    companion object {
        const val PREVIEW_MAX_DIMENSION: Int = DEFAULT_PREVIEW_MAX_DIMENSION
    }
}