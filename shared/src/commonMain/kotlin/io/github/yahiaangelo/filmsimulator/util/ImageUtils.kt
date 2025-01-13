package io.github.yahiaangelo.filmsimulator.util

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Main file for image processing
 */

// List of supported image extensions
val supportedImageExtensions = arrayOf(
    "jpg", "jpeg", "png", "gif", "bmp", "webp",
    "tiff", "tif", "heif", "heic", "ico",
    "svg", "raw", "dng", "cr2", "nef", "orf",
    "arw", "raf", "pef", "sr2", "rw2"
)

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

expect suspend fun convertImageToJpeg(image: String)