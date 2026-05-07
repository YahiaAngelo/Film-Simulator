package io.github.yahiaangelo.filmsimulator.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation of RealtimeImageProcessor
 * Keeps images in native memory for zero-copy, real-time adjustments
 *
 * This processor eliminates file I/O during slider adjustments by:
 * - Loading image once into native memory
 * - Applying adjustments in-place without allocations
 * - Rendering directly to Surface without file writes
 * - Only writing to file when exporting
 */
actual class RealtimeImageProcessor {

    // Native handle to the image in C++ memory
    private var currentImageHandle: Long = 0L
    private var isInitialized = false

    init {
        System.loadLibrary("filmsimulator")
        initializeNative()
        isInitialized = true
    }

    /**
     * Load image from file path into native memory
     * Returns a handle for future operations
     */
    actual suspend fun loadImage(imagePath: String): Long = withContext(Dispatchers.IO) {
        // Release previous image if any
        if (currentImageHandle != 0L) {
            releaseImageNative(currentImageHandle)
        }

        // Load bitmap from file
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }

        val bitmap = BitmapFactory.decodeFile(imagePath, options)
            ?: throw IllegalArgumentException("Failed to decode image: $imagePath")

        // Pass to native for persistent storage
        currentImageHandle = loadImageNative(bitmap)

        // We can recycle the bitmap now - native has its own copy
        bitmap.recycle()

        println("RealtimeImageProcessor: Image loaded into native memory, handle=$currentImageHandle")
        currentImageHandle
    }

    /**
     * Load image from ByteArray into native memory
     */
    actual suspend fun loadImageBytes(imageBytes: ByteArray): Long = withContext(Dispatchers.IO) {
        // Release previous image if any
        if (currentImageHandle != 0L) {
            releaseImageNative(currentImageHandle)
        }

        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw IllegalArgumentException("Failed to decode image bytes")

        currentImageHandle = loadImageNative(bitmap)
        bitmap.recycle()

        currentImageHandle
    }

    /**
     * Update adjustments in real-time
     * This is called on every slider change - NO FILE I/O!
     */
    actual suspend fun updateAdjustments(
        handle: Long,
        exposure: Float,
        contrast: Float,
        brightness: Float,
        saturation: Float,
        temperature: Float,
        grain: Float,
        chromaticAberration: Float
    ): Boolean = withContext(Dispatchers.IO) {
        if (handle == 0L) {
            println("RealtimeImageProcessor: Invalid handle")
            return@withContext false
        }

        // Direct native call - no file I/O, no bitmap creation
        // Processing happens in-place in native memory
        val success = updateAdjustmentsNative(
            handle,
            exposure,
            contrast,
            brightness,
            saturation,
            temperature,
            grain,
            chromaticAberration
        )

        if (success) {
            println("RealtimeImageProcessor: Adjustments updated in native memory (no file I/O)")
        }

        success
    }

    /**
     * Load and cache LUT in native memory
     */
    actual suspend fun loadLUT(lutPath: String): Boolean = withContext(Dispatchers.IO) {
        loadLUTNative(lutPath)
    }

    /**
     * Apply cached LUT to current image
     */
    actual suspend fun applyLUT(handle: Long): Boolean = withContext(Dispatchers.IO) {
        if (handle == 0L) return@withContext false
        applyLUTNative(handle)
    }

    /**
     * Render directly to Android Surface (zero-copy)
     * This is called to display the image - NO FILE I/O!
     */
    actual suspend fun renderToSurface(handle: Long, surface: Any): Boolean = withContext(Dispatchers.IO) {
        if (handle == 0L) return@withContext false
        if (surface !is Surface) return@withContext false

        renderToSurfaceNative(handle, surface)
    }

    /**
     * Get processed bitmap for preview (only when needed)
     * This creates a new bitmap from native memory
     */
    actual suspend fun getProcessedBitmap(handle: Long): Any? = withContext(Dispatchers.IO) {
        if (handle == 0L) return@withContext null
        getProcessedBitmapNative(handle)
    }

    /**
     * Export image to file (only when saving)
     * This is the ONLY time we write to disk
     */
    actual suspend fun exportImage(
        handle: Long,
        outputPath: String,
        quality: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (handle == 0L) return@withContext false

        println("RealtimeImageProcessor: Exporting image from native memory to: $outputPath")
        exportImageNative(handle, outputPath, quality)
    }

    /**
     * Get image dimensions from native
     */
    actual suspend fun getImageDimensions(handle: Long): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        if (handle == 0L) return@withContext null

        val dimensions = getImageDimensionsNative(handle)
        if (dimensions != null && dimensions.size == 2) {
            Pair(dimensions[0], dimensions[1])
        } else {
            null
        }
    }

    /**
     * Release image from native memory
     */
    actual fun releaseImage(handle: Long) {
        if (handle != 0L) {
            releaseImageNative(handle)
            if (handle == currentImageHandle) {
                currentImageHandle = 0L
            }
            println("RealtimeImageProcessor: Image released from native memory")
        }
    }

    /**
     * Clean up native resources
     */
    actual fun cleanup() {
        if (currentImageHandle != 0L) {
            releaseImageNative(currentImageHandle)
            currentImageHandle = 0L
        }
        cleanupNative()
        isInitialized = false
    }

    // Native methods - JNI calls to C++
    private external fun initializeNative()
    private external fun loadImageNative(bitmap: Bitmap): Long
    private external fun updateAdjustmentsNative(
        handle: Long,
        exposure: Float,
        contrast: Float,
        brightness: Float,
        saturation: Float,
        temperature: Float,
        grain: Float,
        chromaticAberration: Float
    ): Boolean
    private external fun loadLUTNative(lutPath: String): Boolean
    private external fun applyLUTNative(handle: Long): Boolean
    private external fun getProcessedBitmapNative(handle: Long): Bitmap?
    private external fun renderToSurfaceNative(handle: Long, surface: Surface): Boolean
    private external fun exportImageNative(handle: Long, outputPath: String, quality: Int): Boolean
    private external fun releaseImageNative(handle: Long)
    private external fun getImageDimensionsNative(handle: Long): IntArray?
    private external fun cleanupNative()

    companion object {
        init {
            System.loadLibrary("filmsimulator")
        }
    }
}