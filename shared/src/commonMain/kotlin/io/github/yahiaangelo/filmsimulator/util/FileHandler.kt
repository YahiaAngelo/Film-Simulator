package util

import io.github.yahiaangelo.filmsimulator.screens.settings.ExportFormat
import io.github.yahiaangelo.filmsimulator.util.AppContext
import okio.Path

const val IMAGE_FILE_NAME = "image.jpeg"
const val EDITED_IMAGE_FILE_NAME = "image-new.jpeg"
const val THUMBNAILS_DIR = "thumbnails"
const val ORIGINAL_IMAGE_FILE_PREFIX = "image-original"

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
 * Export image to gallery.
 *
 * @param image          Filename (in app cache) holding the *processed* JPEG bytes to export.
 * @param appContext     Platform context.
 * @param format         Whether to export as JPEG (re-encode, no metadata) or in the
 *                       original source format with EXIF metadata preserved.
 * @param originalImage  Filename (in app cache) holding the *unmodified* original source
 *                       bytes — used to read EXIF / detect source format. Ignored when
 *                       [format] is [ExportFormat.JPEG] or this is null.
 */
expect suspend fun saveImageToGallery(
    image: String,
    appContext: AppContext,
    format: ExportFormat = ExportFormat.JPEG,
    originalImage: String? = null,
)

/**
 * Create a directory
 */
expect suspend fun createDirectory(directoryName: String)