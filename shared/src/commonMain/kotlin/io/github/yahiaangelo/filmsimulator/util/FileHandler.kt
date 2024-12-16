package util

import androidx.compose.ui.graphics.ImageBitmap
import io.github.yahiaangelo.filmsimulator.util.AppContext

/**
 * Save an image file to cache
 */
expect fun saveImageFile(fileName: String, image: ByteArray)

/**
 * Read an image file from cache
 */
expect suspend fun readImageFile(fileName: String): ByteArray

/**
 * Save cube lut file to cache
 */
expect fun saveLutFile(fileName: String, lut: ByteArray)

/**
 * Export image to gallery
 */
expect suspend fun saveImageToGallery(image: ByteArray, appContext: AppContext)