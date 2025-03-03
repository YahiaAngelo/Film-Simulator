package util

import io.github.yahiaangelo.filmsimulator.util.AppContext
import okio.Path

const val IMAGE_FILE_NAME = "image.jpeg"
const val EDITED_IMAGE_FILE_NAME = "image-new.jpeg"

expect val systemTemporaryPath: Path
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
expect suspend fun saveImageToGallery(image: String, appContext: AppContext)