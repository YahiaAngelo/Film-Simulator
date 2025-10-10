package util

import io.github.yahiaangelo.filmsimulator.util.CoreImageLUTProcessor
import io.github.yahiaangelo.filmsimulator.util.NativeImageProcessor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSLog

@OptIn(ExperimentalForeignApi::class)
actual suspend fun apply3dLutAsync(inputFile: String, lutFile: String, outputFile: String, isThumbnail: Boolean, onComplete: () -> Unit, onError: (String) -> Unit) {
    val inputFileDir = "$systemTemporaryPath/$inputFile"
    val outputFileDir = if (isThumbnail) "$systemTemporaryPath/$THUMBNAILS_DIR/$outputFile" else "$systemTemporaryPath/$outputFile"
    val lutFileDir = "$systemTemporaryPath/$lutFile"

    deleteFile(outputFileDir)
    withContext(Dispatchers.IO) {
        try {
            var success = false

            // Try native Metal processing first
            NSLog("[FFMPEGHandler.ios] Attempting native Metal processing...")

            val nativeProcessor = NativeImageProcessor()
            if (nativeProcessor.isNativeProcessingAvailable()) {
                NSLog("[FFMPEGHandler.ios] ✓ Native Metal processing is available")

                success = nativeProcessor.processImageWithLUT(
                    inputPath = inputFileDir,
                    outputPath = outputFileDir,
                    lutPath = lutFileDir,
                    createThumbnail = isThumbnail
                )

                if (success) {
                    NSLog("[FFMPEGHandler.ios] ✓ Successfully processed with Metal")
                } else {
                    NSLog("[FFMPEGHandler.ios] Metal processing returned false, falling back to Core Image")
                }
            } else {
                NSLog("[FFMPEGHandler.ios] ✗ Native Metal processing not available, using Core Image")
            }

            // If Metal didn't work, fall back to Core Image
            if (!success) {
                NSLog("[FFMPEGHandler.ios] Using Core Image processor...")
                val processor = CoreImageLUTProcessor()
                success = processor.applyLUT(
                    inputPath = inputFileDir,
                    outputPath = outputFileDir,
                    lutPath = lutFileDir,
                    createThumbnail = isThumbnail
                )

                if (success) {
                    NSLog("[FFMPEGHandler.ios] ✓ Successfully processed with Core Image")
                }
            }

            if (success) {
                onComplete()
            } else {
                onError("LUT processing failed")
            }
        } catch (e: Exception) {
            NSLog("[FFMPEGHandler.ios] Processing error: ${e.message}")
            onError("LUT processing error: ${e.message}")
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun apply3dLut(
    inputFile: String,
    lutFile: String,
    outputFile: String,
    isThumbnail: Boolean
): Boolean {
    val inputFileDir = "$systemTemporaryPath/$inputFile"
    val outputFileDir = if (isThumbnail) "$systemTemporaryPath/$THUMBNAILS_DIR/$outputFile" else "$systemTemporaryPath/$outputFile"
    val lutFileDir = "$systemTemporaryPath/$lutFile"

    deleteFile(outputFileDir)

    return try {
        var success = false

        // Try native Metal processing first
        val nativeProcessor = NativeImageProcessor()
        if (nativeProcessor.isNativeProcessingAvailable()) {
            success = nativeProcessor.processImageWithLUT(
                inputPath = inputFileDir,
                outputPath = outputFileDir,
                lutPath = lutFileDir,
                createThumbnail = isThumbnail
            )
        }

        // Fall back to Core Image if Metal didn't work
        if (!success) {
            val processor = CoreImageLUTProcessor()
            success = processor.applyLUT(
                inputPath = inputFileDir,
                outputPath = outputFileDir,
                lutPath = lutFileDir,
                createThumbnail = isThumbnail
            )
        }

        success
    } catch (e: Exception) {
        NSLog("[FFMPEGHandler.ios] Processing error (sync): ${e.message}")
        false
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun addFilmGrain(
    inputFile: String,
    outputFile: String,
    intensity: Float,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    val inputFileDir = "$systemTemporaryPath/$inputFile"
    val outputFileDir = "$systemTemporaryPath/$outputFile"

    deleteFile(outputFileDir)
    withContext(Dispatchers.IO) {
        try {
            val processor = CoreImageLUTProcessor()
            val success = processor.addGrain(
                inputPath = inputFileDir,
                outputPath = outputFileDir,
                intensity = intensity
            )

            if (success) {
                onComplete()
            } else {
                onError("Core Image grain processing failed")
            }
        } catch (e: Exception) {
            onError("Core Image grain processing error: ${e.message}")
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun applyFilters(
    command: String,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    // This function was for custom FFmpeg commands
    // For now, we'll just call onError since we've replaced specific functions
    // In the future, you could parse the command and route to appropriate native functions
    onError("Custom filters not supported in native implementation")
}