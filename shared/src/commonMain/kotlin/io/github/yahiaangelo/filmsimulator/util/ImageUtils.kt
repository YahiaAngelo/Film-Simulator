package io.github.yahiaangelo.filmsimulator.util

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Main file for image processing
 */

/**
 * Add film grain to an image
 */
expect suspend fun addGrain(imageBitmap: ImageBitmap, intensity: Int): ImageBitmap

/**
 * Convert ImageBitmap to a byte array
 */
expect suspend fun ImageBitmap.readPixels(): ByteArray

/**
 * Fix image's Exif orientation
 */
expect suspend fun fixImageOrientation(image: String): String