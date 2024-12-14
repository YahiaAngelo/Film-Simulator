package io.github.yahiaangelo.filmsimulator.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

actual suspend fun ByteArray.fixImageOrientation(): ByteArray {
    return this //Image picker library already fixes it
}