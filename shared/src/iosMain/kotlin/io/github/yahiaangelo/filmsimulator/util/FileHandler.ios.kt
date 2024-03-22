package util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.yahiaangelo.filmsimulator.util.AppContext
import io.github.yahiaangelo.filmsimulator.util.readPixels
import io.github.yahiaangelo.filmsimulator.util.toUIImage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.skia.Image
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIImageWriteToSavedPhotosAlbum

val systemTemporaryPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
actual fun saveImageFile(fileName: String, image: ByteArray) {
  val path = "${systemTemporaryPath/fileName}".toPath()
  FileSystem.SYSTEM.write(path) {
    write(image)
  }
}

actual suspend fun readImageFile(fileName: String): ImageBitmap {
  val path = "${systemTemporaryPath/fileName}".toPath()

  return Image.makeFromEncoded(FileSystem.SYSTEM.read(path) {
    readByteArray()
  }).toComposeImageBitmap()
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

@OptIn(ExperimentalForeignApi::class)
actual suspend fun saveImageToGallery(image: ImageBitmap, appContext: AppContext) {
  val uiImage = image.readPixels().toUIImage()!!

  UIImageWriteToSavedPhotosAlbum(uiImage, null, null, null)
}
