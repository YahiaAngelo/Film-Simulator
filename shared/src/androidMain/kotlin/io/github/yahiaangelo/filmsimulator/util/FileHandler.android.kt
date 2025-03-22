package util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import io.github.yahiaangelo.filmsimulator.data.source.local.SettingsStorageImpl
import io.github.yahiaangelo.filmsimulator.util.AppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.decodeToImageBitmap
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

actual suspend fun saveImageToGallery(image: String, appContext: AppContext) {
    val context: Context = appContext.get()!!
    val settings = SettingsStorageImpl()

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "image_${System.currentTimeMillis()}")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let {
        resolver.openOutputStream(it).use { outputStream ->
            if (outputStream != null) {
                readImageFile(image).decodeToImageBitmap().asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, settings.exportQuality, outputStream)
            }
        }
    } ?: throw IOException("Failed to create new MediaStore record.")
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