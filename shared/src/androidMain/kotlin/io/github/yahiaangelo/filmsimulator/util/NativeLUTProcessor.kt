package io.github.yahiaangelo.filmsimulator.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class NativeLUTProcessor {
    companion object {
        init {
            System.loadLibrary("filmsimulator")
        }
    }

    external fun applyLUTNative(
        inputBitmap: Bitmap,
        outputBitmap: Bitmap,
        lutPath: String
    ): Boolean

    external fun applyLUTToFile(
        inputPath: String,
        outputPath: String,
        lutPath: String,
        createThumbnail: Boolean
    ): Boolean

    external fun addFilmGrainNative(
        bitmap: Bitmap,
        intensity: Float
    )

    suspend fun applyLUT(
        inputPath: String,
        outputPath: String,
        lutPath: String,
        createThumbnail: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Load input image as bitmap
            val inputBitmap = BitmapFactory.decodeFile(inputPath)
                ?: return@withContext false

            // For processing, we always work with the original size
            // and handle scaling separately if needed
            val processingBitmap = inputBitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: return@withContext false

            val outputBitmap = Bitmap.createBitmap(
                processingBitmap.width,
                processingBitmap.height,
                Bitmap.Config.ARGB_8888
            )

            // Apply LUT (always pass false for createThumbnail to avoid complex scaling in C++)
            val success = applyLUTNative(processingBitmap, outputBitmap, lutPath)

            if (success) {
                // Handle thumbnail scaling after LUT processing if needed
                val finalBitmap = if (createThumbnail) {
                    val thumbWidth = 320
                    val thumbHeight = (outputBitmap.height * (320f / outputBitmap.width)).toInt()
                    Bitmap.createScaledBitmap(outputBitmap, thumbWidth, thumbHeight, true)
                } else {
                    outputBitmap
                }

                // Save output bitmap
                val outputFile = File(outputPath)
                outputFile.parentFile?.mkdirs()

                outputFile.outputStream().use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                // Clean up thumbnail bitmap if it was created
                if (createThumbnail && finalBitmap != outputBitmap) {
                    finalBitmap.recycle()
                }
            }

            // Clean up
            inputBitmap.recycle()
            processingBitmap.recycle()
            outputBitmap.recycle()

            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun addGrain(
        imagePath: String,
        outputPath: String,
        intensity: Float
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(imagePath)?.copy(
                Bitmap.Config.ARGB_8888,
                true
            ) ?: return@withContext false

            addFilmGrainNative(bitmap, intensity)

            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            outputFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            bitmap.recycle()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}