package io.github.yahiaangelo.filmsimulator.util

/**
 * Realtime Image Processor for zero-copy, real-time adjustments
 *
 * This processor keeps images in native/GPU memory to enable:
 * - Real-time slider adjustments without file I/O
 * - Direct rendering to UI surfaces
 * - Minimal memory allocations
 * - 60+ fps performance
 *
 * Platform implementations:
 * - Android: Uses C++ with persistent memory and OpenGL
 * - iOS: Uses Metal with GPU textures
 */
expect class RealtimeImageProcessor() {

    /**
     * Load image from file path into native/GPU memory
     * Returns a handle for future operations
     */
    suspend fun loadImage(imagePath: String): Long

    /**
     * Load image from ByteArray into native/GPU memory
     */
    suspend fun loadImageBytes(imageBytes: ByteArray): Long

    /**
     * Update adjustments in real-time
     * This is called on every slider change
     *
     * No file I/O occurs - adjustments are applied in-place
     * in native/GPU memory
     */
    suspend fun updateAdjustments(
        handle: Long,
        exposure: Float,
        contrast: Float,
        brightness: Float,
        saturation: Float,
        temperature: Float,
        grain: Float,
        chromaticAberration: Float
    ): Boolean

    /**
     * Load and cache LUT in native/GPU memory
     * The LUT stays in memory for fast application
     */
    suspend fun loadLUT(lutPath: String): Boolean

    /**
     * Apply cached LUT to current image
     */
    suspend fun applyLUT(handle: Long): Boolean

    /**
     * Render directly to platform surface
     * Android: Surface
     * iOS: MTKView or CAMetalLayer
     *
     * Zero-copy rendering - no file I/O
     */
    suspend fun renderToSurface(handle: Long, surface: Any): Boolean

    /**
     * Get processed image for preview or sharing
     * Android: Returns Bitmap
     * iOS: Returns UIImage
     *
     * Creates a new image from native/GPU memory
     */
    suspend fun getProcessedBitmap(handle: Long): Any?

    /**
     * Export image to file
     * This is the only time file I/O occurs
     */
    suspend fun exportImage(
        handle: Long,
        outputPath: String,
        quality: Int = 95
    ): Boolean

    /**
     * Get image dimensions
     */
    suspend fun getImageDimensions(handle: Long): Pair<Int, Int>?

    /**
     * Release image from native/GPU memory
     */
    fun releaseImage(handle: Long)

    /**
     * Clean up all native resources
     */
    fun cleanup()
}

/**
 * Data class for passing adjustment values efficiently
 */
data class RealtimeAdjustments(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val brightness: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val grain: Float = 0f,
    val chromaticAberration: Float = 0f
) {
    /**
     * Convert to normalized values for native processing
     */
    fun toNormalized(): RealtimeAdjustments {
        return RealtimeAdjustments(
            exposure = (exposure / 20f).coerceIn(-2f, 2f),
            contrast = (contrast / 10f).coerceIn(-1f, 1f),
            brightness = (brightness / 20f).coerceIn(-1f, 1f),
            saturation = (saturation / 20f).coerceIn(-1f, 1f),
            temperature = (temperature / 20f).coerceIn(-1f, 1f),
            grain = (grain / 10f).coerceIn(0f, 1f),
            chromaticAberration = (chromaticAberration / 10f).coerceIn(0f, 1f)
        )
    }

    fun hasAdjustments(): Boolean {
        return exposure != 0f || contrast != 0f || brightness != 0f ||
               saturation != 0f || temperature != 0f || grain != 0f ||
               chromaticAberration != 0f
    }
}