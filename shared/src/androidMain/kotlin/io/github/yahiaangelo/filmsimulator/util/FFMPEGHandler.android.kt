package util

import io.github.yahiaangelo.filmsimulator.util.NativeLUTProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun apply3dLutAsync(inputFile: String, lutFile: String, outputFile: String, isThumbnail: Boolean, onComplete: () -> Unit, onError: (String) -> Unit) {
    val inputFileDir = "$systemTemporaryPath/$inputFile"
    val outputFileDir = if (isThumbnail) "$systemTemporaryPath/$THUMBNAILS_DIR/$outputFile" else "$systemTemporaryPath/$outputFile"
    val lutFileDir = "$systemTemporaryPath/$lutFile"

    deleteFile(outputFileDir)
    withContext(Dispatchers.IO) {
        try {
            val processor = NativeLUTProcessor()
            val success = processor.applyLUT(
                inputPath = inputFileDir,
                outputPath = outputFileDir,
                lutPath = lutFileDir,
                createThumbnail = isThumbnail
            )

            if (success) {
                onComplete()
            } else {
                onError("Native LUT processing failed")
            }
        } catch (e: Exception) {
            onError("Native LUT processing error: ${e.message}")
        }
    }
}

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
        val processor = NativeLUTProcessor()
        processor.applyLUT(
            inputPath = inputFileDir,
            outputPath = outputFileDir,
            lutPath = lutFileDir,
            createThumbnail = isThumbnail
        )
    } catch (e: Exception) {
        false
    }
}

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
            val processor = NativeLUTProcessor()
            val success = processor.addGrain(
                imagePath = inputFileDir,
                outputPath = outputFileDir,
                intensity = intensity
            )

            if (success) {
                onComplete()
            } else {
                onError("Native grain processing failed")
            }
        } catch (e: Exception) {
            onError("Native grain processing error: ${e.message}")
        }
    }
}

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
