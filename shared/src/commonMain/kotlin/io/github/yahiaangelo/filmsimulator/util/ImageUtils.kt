package io.github.yahiaangelo.filmsimulator.util

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Main file for image processing
 */

expect suspend fun addGrain(imageBitmap: ImageBitmap, intensity: Int): ImageBitmap

expect suspend fun ImageBitmap.readPixels(): ByteArray


