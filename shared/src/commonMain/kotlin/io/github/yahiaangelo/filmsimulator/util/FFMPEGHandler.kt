package util

/**
 * Main function to apply cube lut to an image using FFMPEG
 */
expect suspend fun apply3dLutAsync(inputFile: String, lutFile: String, outputFile: String, isThumbnail: Boolean = false, onComplete: () -> Unit, onError: (String) -> Unit)

expect suspend fun apply3dLut(inputFile: String, lutFile: String, outputFile: String, isThumbnail: Boolean = false) : Boolean

expect suspend fun addFilmGrain(inputFile: String, outputFile: String, intensity: Float, onComplete: () -> Unit, onError: (String) -> Unit)

expect suspend fun applyFilters(command: String, onComplete: () -> Unit, onError: (String) -> Unit)
