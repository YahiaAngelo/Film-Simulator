package io.github.yahiaangelo.filmsimulator.image

import io.github.yahiaangelo.filmsimulator.image.shaders.LutShader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import kotlin.math.min

private const val THUMBNAIL_MAX_WIDTH = 320

/**
 * Cross-platform 3D LUT image processor backed by Skia (skiko). Replaces the previous
 * Android C++/NDK and iOS Core Image / Metal pipelines with one shader-based path that
 * runs identically on every Skia-capable target.
 *
 * Pipeline:
 *   1. Decode the encoded source image with [Image.makeFromEncoded].
 *   2. Parse the .cube LUT and pack it into a small `lutSize x (lutSize * lutSize)`
 *      RGBA image (one slice per blue index, stacked vertically).
 *   3. Render the source through [LutShader.SHADER] into a raster [Surface] sized to the
 *      requested output dimensions (the shader's `imageScale` uniform handles thumbnail
 *      downscaling without an extra resize pass).
 *   4. Encode the snapshot as JPEG and return the bytes.
 */
class SkiaLutProcessor {

    data class Lut3D(val size: Int, val data: FloatArray) {
        override fun equals(other: Any?): Boolean =
            other is Lut3D && other.size == size && other.data.contentEquals(data)

        override fun hashCode(): Int = 31 * size + data.contentHashCode()
    }

    /**
     * Apply [lutBytes] (.cube content) to [imageBytes] and return the encoded JPEG result.
     * Returns null if the LUT cannot be parsed or the image cannot be decoded.
     */
    suspend fun applyLut(
        imageBytes: ByteArray,
        lutBytes: ByteArray,
        createThumbnail: Boolean = false,
        quality: Int = 95,
    ): ByteArray? = withContext(Dispatchers.Default) {
        val lut = parseCubeLut(lutBytes.decodeToString()) ?: return@withContext null
        val source = runCatching { Image.makeFromEncoded(imageBytes) }.getOrNull() ?: return@withContext null

        val (outWidth, outHeight) = computeOutputSize(source.width, source.height, createThumbnail)
        val lutImage = buildLutImage(lut)

        val effect = RuntimeEffect.makeForShader(LutShader.SHADER)
        val builder = RuntimeShaderBuilder(effect).apply {
            uniform("lutSize", lut.size.toFloat())
            uniform(
                "imageScale",
                source.width.toFloat() / outWidth,
                source.height.toFloat() / outHeight,
            )
            child("image", source.makeShader(
                tmx = FilterTileMode.CLAMP,
                tmy = FilterTileMode.CLAMP,
                sampling = SamplingMode.LINEAR,
                localMatrix = null,
            ))
            child("lut", lutImage.makeShader(
                tmx = FilterTileMode.CLAMP,
                tmy = FilterTileMode.CLAMP,
                sampling = SamplingMode.DEFAULT,
                localMatrix = null,
            ))
        }

        val info = ImageInfo(outWidth, outHeight, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        val surface = Surface.makeRaster(info)
        val paint = Paint().apply { shader = builder.makeShader() }
        surface.canvas.drawRect(Rect(0f, 0f, outWidth.toFloat(), outHeight.toFloat()), paint)

        val snapshot = surface.makeImageSnapshot()
        snapshot.encodeToData(EncodedImageFormat.JPEG, quality)?.bytes
    }

    /**
     * Pack the parsed LUT into a Skia [Image] laid out as a vertical stack of slices:
     * width = lutSize, height = lutSize * lutSize. The byte index for cell (r, g, b) is
     * `(r + g * size + b * size * size) * 4`, which matches both the .cube file order and
     * the indexing performed by [LutShader.SHADER].
     */
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

    private fun computeOutputSize(srcW: Int, srcH: Int, thumbnail: Boolean): Pair<Int, Int> {
        if (!thumbnail) return srcW to srcH
        val w = min(THUMBNAIL_MAX_WIDTH, srcW)
        val h = (srcH.toFloat() * w / srcW).toInt().coerceAtLeast(1)
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
        // Some LUT files include trailing metadata-as-floats; clamp to the expected length.
        return Lut3D(size, data.subList(0, expected).toFloatArray())
    }
}
