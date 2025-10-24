package io.github.yahiaangelo.filmsimulator.util

/**
 * Data class representing all image adjustments that can be applied natively
 */
data class NativeImageAdjustments(
    val contrast: Float = 0f,        // Range: -1.0 to 1.0
    val brightness: Float = 0f,      // Range: -1.0 to 1.0
    val saturation: Float = 0f,      // Range: -1.0 to 1.0
    val temperature: Float = 0f,     // Range: -1.0 to 1.0 (blue to orange)
    val exposure: Float = 0f,        // Range: -2.0 to 2.0
    val grain: Float = 0f,           // Range: 0.0 to 1.0
    val chromaticAberration: Float = 0f  // Range: 0.0 to 1.0
) {
    /**
     * Check if any adjustments are applied
     */
    fun hasAdjustments(): Boolean {
        return contrast != 0f || brightness != 0f || saturation != 0f ||
                temperature != 0f || exposure != 0f || grain != 0f ||
                chromaticAberration != 0f
    }

    /**
     * Check if all adjustments are at default values
     */
    fun isDefault(): Boolean = !hasAdjustments()
}

/**
 * Native image adjustment processor interface
 * Platform-specific implementations will handle image processing using native APIs
 */
expect class NativeImageAdjustmentProcessor() {
    /**
     * Apply image adjustments to an image file
     *
     * @param inputPath Path to the input image
     * @param outputPath Path where the processed image will be saved
     * @param adjustments The adjustments to apply
     * @param forExport If true, uses full quality (no downscaling). If false, downscales for preview performance.
     * @return true if processing was successful, false otherwise
     */
    suspend fun applyAdjustments(
        inputPath: String,
        outputPath: String,
        adjustments: NativeImageAdjustments,
        forExport: Boolean = false
    ): Boolean

    /**
     * Apply both LUT and image adjustments in a single pass
     * This is more efficient than applying them separately
     *
     * @param inputPath Path to the input image
     * @param outputPath Path where the processed image will be saved
     * @param lutPath Path to the 3D LUT file (.cube format)
     * @param adjustments The adjustments to apply
     * @param createThumbnail If true, creates a thumbnail-sized output
     * @return true if processing was successful, false otherwise
     */
    suspend fun applyLutAndAdjustments(
        inputPath: String,
        outputPath: String,
        lutPath: String,
        adjustments: NativeImageAdjustments,
        createThumbnail: Boolean = false
    ): Boolean

    /**
     * Check if native processing is available on this platform
     */
    fun isAvailable(): Boolean
}