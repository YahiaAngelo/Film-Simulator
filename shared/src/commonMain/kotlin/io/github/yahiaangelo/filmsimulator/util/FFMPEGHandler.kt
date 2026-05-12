package util

import io.github.yahiaangelo.filmsimulator.image.SkiaImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem

/**
 * Cross-platform image-processing entry points used by [io.github.yahiaangelo.filmsimulator.data.source.DefaultFilmRepository]
 * and the screen models.
 *
 * Backed by [SkiaImageProcessor], so the same code path runs on every Skia-capable target
 * (Android + iOS today). The previous expect/actual implementation routed to FFmpeg-kit,
 * Android NDK C++, or iOS Core Image / Metal — all of which have been retired.
 *
 * Filenames are resolved relative to [systemTemporaryPath]; thumbnails go into the
 * [THUMBNAILS_DIR] subdirectory under the same root.
 */

private val processor = SkiaImageProcessor()

suspend fun apply3dLutAsync(
    inputFile: String,
    lutFile: String,
    outputFile: String,
    isThumbnail: Boolean = false,
    onComplete: () -> Unit,
    onError: (String) -> Unit,
) {
    if (apply3dLut(inputFile, lutFile, outputFile, isThumbnail)) onComplete()
    else onError("Skia LUT processing failed")
}

suspend fun apply3dLut(
    inputFile: String,
    lutFile: String,
    outputFile: String,
    isThumbnail: Boolean = false,
): Boolean = withContext(Dispatchers.Default) {
    val inputPath = (systemTemporaryPath / inputFile)
    val lutPath = (systemTemporaryPath / lutFile)
    val outputPath = if (isThumbnail) (systemTemporaryPath / THUMBNAILS_DIR / outputFile)
                     else (systemTemporaryPath / outputFile)

    runCatching {
        val imageBytes = FileSystem.SYSTEM.read(inputPath) { readByteArray() }
        val lutBytes = FileSystem.SYSTEM.read(lutPath) { readByteArray() }

        val resultBytes = processor.applyLut(
            imageBytes = imageBytes,
            lutBytes = lutBytes,
            createThumbnail = isThumbnail,
        ) ?: return@runCatching false

        outputPath.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
        FileSystem.SYSTEM.delete(outputPath, mustExist = false)
        FileSystem.SYSTEM.write(outputPath) { write(resultBytes) }
        true
    }.getOrElse { false }
}
