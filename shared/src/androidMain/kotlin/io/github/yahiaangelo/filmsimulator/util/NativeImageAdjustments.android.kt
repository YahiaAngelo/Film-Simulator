package io.github.yahiaangelo.filmsimulator.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import util.readImageFile
import util.saveImageFile
import java.io.ByteArrayOutputStream

actual class NativeImageAdjustmentProcessor {

    companion object {
        init {
            System.loadLibrary("filmsimulator")
        }
    }

    /**
     * Apply image adjustments to an image file
     */
    actual suspend fun applyAdjustments(
        inputPath: String,
        outputPath: String,
        adjustments: NativeImageAdjustments,
        forExport: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Load input image using FileHandler which handles paths correctly
            val inputBytes = readImageFile(inputPath)
            var inputBitmap = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size)
                ?: return@withContext false

            // Downscale for preview only (not for export)
            if (!forExport) {
                val maxDimension = 1920
                if (inputBitmap.width > maxDimension || inputBitmap.height > maxDimension) {
                    val scale = maxDimension.toFloat() / maxOf(inputBitmap.width, inputBitmap.height)
                    val newWidth = (inputBitmap.width * scale).toInt()
                    val newHeight = (inputBitmap.height * scale).toInt()

                    val scaledBitmap = Bitmap.createScaledBitmap(inputBitmap, newWidth, newHeight, true)
                    inputBitmap.recycle()
                    inputBitmap = scaledBitmap
                }
            }

            // Create output bitmap (mutable copy)
            val outputBitmap = inputBitmap.copy(Bitmap.Config.ARGB_8888, true)

            // Apply adjustments via native code
            val success = applyAdjustmentsNative(
                inputBitmap = inputBitmap,
                outputBitmap = outputBitmap,
                contrast = adjustments.contrast,
                brightness = adjustments.brightness,
                saturation = adjustments.saturation,
                temperature = adjustments.temperature,
                exposure = adjustments.exposure,
                grain = adjustments.grain,
                chromaticAberration = adjustments.chromaticAberration
            )

            if (success) {
                // Convert bitmap to byte array and save using FileHandler
                val outputStream = ByteArrayOutputStream()
                outputBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                saveImageFile(outputPath, outputStream.toByteArray())
            }

            // Clean up
            inputBitmap.recycle()
            outputBitmap.recycle()

            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Apply both LUT and image adjustments in a single pass
     */
    actual suspend fun applyLutAndAdjustments(
        inputPath: String,
        outputPath: String,
        lutPath: String,
        adjustments: NativeImageAdjustments,
        createThumbnail: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Load input image using FileHandler
            val inputBytes = readImageFile(inputPath)
            var inputBitmap = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size)
                ?: return@withContext false

            // Handle thumbnail scaling
            if (createThumbnail) {
                val thumbnailWidth = 320
                val scale = thumbnailWidth.toFloat() / inputBitmap.width
                val thumbnailHeight = (inputBitmap.height * scale).toInt()

                val scaledBitmap = Bitmap.createScaledBitmap(
                    inputBitmap,
                    thumbnailWidth,
                    thumbnailHeight,
                    true
                )
                inputBitmap.recycle()
                inputBitmap = scaledBitmap
            }

            // Create output bitmap (mutable copy)
            val outputBitmap = inputBitmap.copy(Bitmap.Config.ARGB_8888, true)

            // Apply LUT and adjustments via native code
            val success = applyLutAndAdjustmentsNative(
                inputBitmap = inputBitmap,
                outputBitmap = outputBitmap,
                lutPath = lutPath,
                contrast = adjustments.contrast,
                brightness = adjustments.brightness,
                saturation = adjustments.saturation,
                temperature = adjustments.temperature,
                exposure = adjustments.exposure,
                grain = adjustments.grain,
                chromaticAberration = adjustments.chromaticAberration
            )

            if (success) {
                // Convert bitmap to byte array and save using FileHandler
                val outputStream = ByteArrayOutputStream()
                outputBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                saveImageFile(outputPath, outputStream.toByteArray())
            }

            // Clean up
            inputBitmap.recycle()
            outputBitmap.recycle()

            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Check if native processing is available
     */
    actual fun isAvailable(): Boolean = true

    // Native methods
    private external fun applyAdjustmentsNative(
        inputBitmap: Bitmap,
        outputBitmap: Bitmap,
        contrast: Float,
        brightness: Float,
        saturation: Float,
        temperature: Float,
        exposure: Float,
        grain: Float,
        chromaticAberration: Float
    ): Boolean

    private external fun applyLutAndAdjustmentsNative(
        inputBitmap: Bitmap,
        outputBitmap: Bitmap,
        lutPath: String,
        contrast: Float,
        brightness: Float,
        saturation: Float,
        temperature: Float,
        exposure: Float,
        grain: Float,
        chromaticAberration: Float
    ): Boolean
}