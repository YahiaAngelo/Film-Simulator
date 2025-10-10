package io.github.yahiaangelo.filmsimulator.util

/**
 * Platform-specific image processor interface.
 * iOS will use Metal, Android uses native C++ implementation.
 */
expect class NativeImageProcessor() {
    /**
     * Process an image with a 3D LUT.
     *
     * @param inputPath Path to the input image
     * @param outputPath Path where the output should be saved
     * @param lutPath Path to the CUBE LUT file
     * @param createThumbnail Whether to create a thumbnail (downscaled) version
     * @return true if processing succeeded, false otherwise
     */
    fun processImageWithLUT(
        inputPath: String,
        outputPath: String,
        lutPath: String,
        createThumbnail: Boolean
    ): Boolean

    /**
     * Check if native (GPU) processing is available on this device.
     * iOS: checks for Metal availability
     * Android: checks for native library
     */
    fun isNativeProcessingAvailable(): Boolean
}
