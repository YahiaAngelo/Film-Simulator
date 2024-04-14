package util

/**
 * Main function to apply cube lut to an image using FFMPEG
 */
expect suspend fun apply3dLut(inputFile: String, lutFile: String, outputFile: String, onComplete: () -> Unit)