package util

import androidx.compose.ui.graphics.ImageBitmap
import io.github.yahiaangelo.filmsimulator.util.AppContext


expect fun saveImageFile(fileName: String, image: ByteArray)

expect suspend fun readImageFile(fileName: String): ImageBitmap

expect fun saveLutFile(fileName: String, lut: ByteArray)

expect suspend fun saveImageToGallery(image: ImageBitmap, appContext: AppContext)