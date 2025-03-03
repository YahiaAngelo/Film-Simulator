package io.github.yahiaangelo.filmsimulator.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import util.systemTemporaryPath
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Main file for image processing
 */
actual suspend fun addGrain(
    imageBitmap: ImageBitmap,
    intensity: Int
): ImageBitmap {
    val bitmap = imageBitmap.asAndroidBitmap()
    val width = bitmap.width
    val height = bitmap.height
    val noise = Random

   return withContext(Dispatchers.IO) {
       // Create a mutable copy of the bitmap to modify
       val processedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)


       // Loop through the pixels
       for (x in 0 until width) {
           for (y in 0 until height) {
               // Get the current pixel
               val pixel = processedBitmap.getPixel(x, y)
               val alpha = pixel shr 24 and 0xff
               val red = pixel shr 16 and 0xff
               val green = pixel shr 8 and 0xff
               val blue = pixel and 0xff

               // Modify the color values to add noise
               val noiseValue = noise.nextInt(-intensity, intensity)
               val r = (red + noiseValue).coerceIn(0, 255)
               val g = (green + noiseValue).coerceIn(0, 255)
               val b = (blue + noiseValue).coerceIn(0, 255)

               // Set the pixel back to the bitmap
               processedBitmap.setPixel(x, y, (alpha shl 24) or (r shl 16) or (g shl 8) or b)
           }
       }

       // Convert back to ImageBitmap if necessary
       processedBitmap.asImageBitmap()
    }

}

actual suspend fun ImageBitmap.readPixels(): ByteArray {
    return withContext(Dispatchers.Main) {
        val stream = ByteArrayOutputStream()
        this@readPixels.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 100, stream)
        stream.toByteArray()
    }

}

actual suspend fun fixImageOrientation(image: String): String = image

actual suspend fun convertImageToJpeg(image: String){
    val imagePath = "$systemTemporaryPath/$image"
    try {
        withContext(Dispatchers.IO) {
            // Load the .heic image as a Bitmap
            val heicBitmap = BitmapFactory.decodeFile(imagePath)

            // Check if Bitmap is successfully loaded
            if (heicBitmap != null) {
                // Read the EXIF orientation metadata
                val exif = ExifInterface(imagePath)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

                // Adjust the Bitmap's orientation
                val adjustedBitmap = adjustBitmapOrientation(heicBitmap, orientation)

                // Create an output file
                val outputFile = File(imagePath)
                val outputStream = FileOutputStream(outputFile)

                // Compress the Bitmap into JPEG format and save it to the output file
                adjustedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)

                // Close the stream
                outputStream.flush()
                outputStream.close()

                println("Conversion successful. JPEG file saved at: $imagePath")
            } else {
                println("Failed to decode .heic file. Ensure the input file is valid.")
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        println("Error during conversion: ${e.message}")
    }
}

private fun adjustBitmapOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()

    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
    }

    return try {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: Exception) {
        e.printStackTrace()
        bitmap // Return the original bitmap if transformation fails
    }
}