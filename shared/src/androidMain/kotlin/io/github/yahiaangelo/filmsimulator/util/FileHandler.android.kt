package util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.yahiaangelo.filmsimulator.util.AppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import java.io.IOException

val systemTemporaryPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY

actual fun saveImageFile(fileName: String, image: ByteArray) {
    val path = "${systemTemporaryPath/fileName}".toPath()
    FileSystem.SYSTEM.write(path) {
        write(image)
    }

}

actual suspend fun readImageFile(fileName: String): ImageBitmap {
    val path = "${systemTemporaryPath/fileName}".toPath()
    var imageByteArray = ByteArray(0)
    FileSystem.SYSTEM.read(path) {
        imageByteArray = readByteArray()
    }

    return BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.size).asImageBitmap()
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

actual suspend fun saveImageToGallery(image: ImageBitmap, appContext: AppContext) {
    val context: Context = appContext.get()!!

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
                image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }
        }
    } ?: throw IOException("Failed to create new MediaStore record.")
}
