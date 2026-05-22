package util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import io.github.yahiaangelo.filmsimulator.data.source.local.SettingsStorageImpl
import io.github.yahiaangelo.filmsimulator.screens.settings.ExportFormat
import io.github.yahiaangelo.filmsimulator.util.AppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.decodeToImageBitmap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

actual val systemTemporaryPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY

actual fun saveImageFile(fileName: String, image: ByteArray) {
    val path = "${systemTemporaryPath/fileName}".toPath()
    FileSystem.SYSTEM.write(path) {
        write(image)
    }

}

actual suspend fun readImageFile(fileName: String): ByteArray {
    val path = "${systemTemporaryPath/fileName}".toPath()
    var imageByteArray = ByteArray(0)
    FileSystem.SYSTEM.read(path) {
        imageByteArray = readByteArray()
    }

    return imageByteArray
}

actual fun saveLutFile(fileName: String, lut: ByteArray) {
    val path = "${systemTemporaryPath/fileName}".toPath()
    FileSystem.SYSTEM.write(path) {
        write(lut)
    }
}

suspend fun deleteFile(filePath: String) {
    withContext(Dispatchers.IO) {
        FileSystem.SYSTEM.delete(filePath.toPath())
    }
}

actual suspend fun saveImageToGallery(
    image: String,
    appContext: AppContext,
    format: ExportFormat,
    originalImage: String?,
) {
    val context: Context = appContext.get()!!
    val settings = SettingsStorageImpl()

    // Decide actual encoding + MIME based on user preference and source format.
    // If the user picked ORIGINAL but we don't have the source file, fall back to JPEG.
    val targetExt = if (format == ExportFormat.ORIGINAL && originalImage != null) {
        originalImage.substringAfterLast('.', "jpeg").lowercase()
    } else {
        "jpeg"
    }
    val (compressFormat, mimeType, fileExt) = resolveEncoding(targetExt)

    val now = System.currentTimeMillis()
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "image_$now.$fileExt")
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/FilmSimulator")
        // Override the gallery's sort-time so the export shows up at the top of
        // the timeline. The EXIF DateTimeOriginal (true capture moment) is still
        // preserved inside the file for archival purposes — see copyExif.
        put(MediaStore.MediaColumns.DATE_TAKEN, now)
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        ?: throw IOException("Failed to create new MediaStore record.")

    val bitmap = readImageFile(image).decodeToImageBitmap().asAndroidBitmap()

    if (format == ExportFormat.ORIGINAL && originalImage != null && mimeType == "image/jpeg") {
        // JPEG output with EXIF: encode to a temp file first so we can rewrite EXIF on it,
        // then stream the result into MediaStore.
        val tmp = File.createTempFile("export_", ".jpg", context.cacheDir)
        try {
            FileOutputStream(tmp).use { out ->
                bitmap.compress(compressFormat, settings.exportQuality, out)
            }
            val sourcePath = "${systemTemporaryPath / originalImage}"
            runCatching { copyExif(sourcePath, tmp.absolutePath) }
            resolver.openOutputStream(uri).use { outputStream ->
                outputStream?.write(tmp.readBytes())
            }
        } finally {
            tmp.delete()
        }
    } else {
        // Either plain JPEG export (original behavior) or a non-JPEG re-encode
        // (PNG / WEBP / HEIC). For non-JPEG, EXIF embedding is not portable enough
        // across Android versions to be worth the complexity; we re-encode in the
        // chosen format so the user at least gets the source file's pixel format.
        resolver.openOutputStream(uri).use { outputStream ->
            if (outputStream != null) {
                bitmap.compress(compressFormat, settings.exportQuality, outputStream)
            }
        }
    }
}

private data class Encoding(
    val format: Bitmap.CompressFormat,
    val mime: String,
    val ext: String,
)

private fun resolveEncoding(srcExt: String): Encoding = when (srcExt) {
    "png" -> Encoding(Bitmap.CompressFormat.PNG, "image/png", "png")
    "webp" -> Encoding(
        @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP,
        "image/webp",
        "webp",
    )
    // Android's Bitmap API does not expose HEIC/HEIF encoding (HeifWriter is the
    // dedicated API and is significantly more complex). For these sources we
    // fall back to JPEG, which still preserves EXIF metadata via the JPEG path.
    else -> Encoding(Bitmap.CompressFormat.JPEG, "image/jpeg", "jpg")
}

/**
 * Copy EXIF tags from a source JPEG/HEIC/whatever into a destination JPEG file.
 * Resets the orientation tag because the destination's pixels have orientation
 * already baked in by the processing pipeline.
 */
private fun copyExif(sourcePath: String, destPath: String) {
    val src = ExifInterface(sourcePath)
    val dst = ExifInterface(destPath)
    // TAG_DATETIME / TAG_SUBSEC_TIME represent the file's last-modified time, not
    // the moment the shutter fired — we intentionally let those default to "now"
    // so the export reads as freshly modified, while DATETIME_ORIGINAL /
    // DATETIME_DIGITIZED preserve the actual capture moment for archival.
    val tagsToCopy = arrayOf(
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
    )
    for (tag in tagsToCopy) {
        src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
    }
    // Pixels are already in display orientation — strip the rotation tag.
    dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
    dst.saveAttributes()
}

/**
 * Create a directory
 */
actual suspend fun createDirectory(directoryName: String) {
    withContext(Dispatchers.IO) {
        val path = "${systemTemporaryPath/directoryName}".toPath()
        FileSystem.SYSTEM.createDirectory(path)
    }
}