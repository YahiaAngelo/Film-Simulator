package io.github.yahiaangelo.filmsimulator.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import util.systemTemporaryPath
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.random.Random

/**
 * Main file for image processing
 */
actual suspend fun addGrain(
    imageBitmap: ImageBitmap,
    intensity: Int
): ImageBitmap {
    val bitmap = imageBitmap.asAndroidBitmap()
    val width = bitmap.width
    val height = bitmap.height
    val noise = Random

   return withContext(Dispatchers.IO) {
       // Create a mutable copy of the bitmap to modify
       val processedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)


       // Loop through the pixels
       for (x in 0 until width) {
           for (y in 0 until height) {
               // Get the current pixel
               val pixel = processedBitmap.getPixel(x, y)
               val alpha = pixel shr 24 and 0xff
               val red = pixel shr 16 and 0xff
               val green = pixel shr 8 and 0xff
               val blue = pixel and 0xff

               // Modify the color values to add noise
               val noiseValue = noise.nextInt(-intensity, intensity)
               val r = (red + noiseValue).coerceIn(0, 255)
               val g = (green + noiseValue).coerceIn(0, 255)
               val b = (blue + noiseValue).coerceIn(0, 255)

               // Set the pixel back to the bitmap
               processedBitmap.setPixel(x, y, (alpha shl 24) or (r shl 16) or (g shl 8) or b)
           }
       }

       // Convert back to ImageBitmap if necessary
       processedBitmap.asImageBitmap()
    }

}

actual suspend fun ImageBitmap.readPixels(): ByteArray {
    return withContext(Dispatchers.Main) {
        val stream = ByteArrayOutputStream()
        this@readPixels.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 100, stream)
        stream.toByteArray()
    }

}

actual suspend fun fixImageOrientation(image: String): String = image

actual suspend fun convertImageToJpeg(image: String) {
    val imagePath = "$systemTemporaryPath/$image"
    try {
        withContext(Dispatchers.IO) {
            // Sniff the file by its first bytes instead of trusting the extension —
            // the picker pipeline always writes the cached source as "image.jpeg"
            // regardless of source format, so a DNG on disk is named .jpeg here.
            val isTiff = runCatching { isTiffMagic(File(imagePath)) }.getOrDefault(false)
            val bitmap: Bitmap? = if (isTiff) {
                // For RAW/DNG (TIFF containers) we walk the structure ourselves and
                // pull the largest embedded JPEG preview, then apply IFD0 orientation
                // since the embedded preview is stored unrotated. Stock Android
                // decoders either pick a smaller preview or skip orientation entirely
                // for DNGs from some cameras.
                decodeRawViaEmbeddedPreview(imagePath)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching {
                    val source = ImageDecoder.createSource(File(imagePath))
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = false
                    }
                }.getOrNull() ?: decodeWithBitmapFactoryAndFixOrientation(imagePath)
            } else {
                decodeWithBitmapFactoryAndFixOrientation(imagePath)
            }

            if (bitmap == null) {
                println("Failed to decode image at $imagePath")
                return@withContext
            }

            FileOutputStream(File(imagePath)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                out.flush()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        println("Error during conversion: ${e.message}")
    }
}

private fun isTiffMagic(file: File): Boolean {
    if (!file.exists() || file.length() < 4) return false
    return RandomAccessFile(file, "r").use { raf ->
        val head = ByteArray(4)
        raf.readFully(head)
        // "II" + 0x2A 0x00 (little-endian TIFF) or "MM" + 0x00 0x2A (big-endian)
        (head[0] == 0x49.toByte() && head[1] == 0x49.toByte() &&
            head[2] == 0x2A.toByte() && head[3] == 0x00.toByte()) ||
        (head[0] == 0x4D.toByte() && head[1] == 0x4D.toByte() &&
            head[2] == 0x00.toByte() && head[3] == 0x2A.toByte())
    }
}

private fun decodeRawViaEmbeddedPreview(imagePath: String): Bitmap? {
    val file = File(imagePath)
    val parsed = runCatching { extractLargestEmbeddedJpegFromRaw(file) }.getOrNull()
    val bitmap = if (parsed != null) {
        BitmapFactory.decodeByteArray(parsed.jpegBytes, 0, parsed.jpegBytes.size)
    } else {
        // If TIFF walking didn't find anything useful, fall back to whatever Skia
        // can decode directly — better than failing outright.
        BitmapFactory.decodeFile(imagePath)
    } ?: return null

    // Trust the TIFF orientation read from our own parser when we have it.
    // android.media.ExifInterface's DNG handling is inconsistent across versions
    // (it returns NORMAL on some devices even when the IFD0 orientation tag is
    // present), so we prefer the value we parsed ourselves and only fall back
    // to ExifInterface if parsing failed.
    val orientation = parsed?.orientation
        ?: runCatching {
            ExifInterface(imagePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    return adjustBitmapOrientation(bitmap, orientation)
}

private fun decodeWithBitmapFactoryAndFixOrientation(imagePath: String): Bitmap? {
    val bm = BitmapFactory.decodeFile(imagePath) ?: return null
    val orientation = runCatching {
        ExifInterface(imagePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    return adjustBitmapOrientation(bm, orientation)
}

private data class ParsedRawPreview(val jpegBytes: ByteArray, val orientation: Int)

/**
 * Walks the TIFF/DNG structure of a RAW file to find the largest embedded JPEG
 * preview. DNG (and most RAWs) are TIFF containers: IFD0 typically holds a small
 * thumbnail (and for "RAW+JPEG" Pixel captures, that's the *only* JPEG — the
 * full image lives in the companion .jpg file, not the .dng). SubIFDs may hold
 * a larger preview on cameras that embed one. We walk every IFD/SubIFD, collect
 * candidates with JPEG-encoded pixels, and pick the largest by dimensions.
 *
 * Also returns the IFD0 orientation tag — needed because embedded preview JPEGs
 * are stored unrotated; the rotation lives on the parent TIFF.
 */
private fun extractLargestEmbeddedJpegFromRaw(file: File): ParsedRawPreview? {
    RandomAccessFile(file, "r").use { raf ->
        val fileLen = raf.length()
        if (fileLen < 8) return null

        val header = ByteArray(8)
        raf.readFully(header)
        val littleEndian = when {
            header[0] == 0x49.toByte() && header[1] == 0x49.toByte() -> true  // 'II'
            header[0] == 0x4D.toByte() && header[1] == 0x4D.toByte() -> false // 'MM'
            else -> return null
        }

        fun u16(buf: ByteArray, o: Int): Int {
            val b0 = buf[o].toInt() and 0xff
            val b1 = buf[o + 1].toInt() and 0xff
            return if (littleEndian) (b1 shl 8) or b0 else (b0 shl 8) or b1
        }
        fun u32(buf: ByteArray, o: Int): Long {
            val b0 = buf[o].toLong() and 0xff
            val b1 = buf[o + 1].toLong() and 0xff
            val b2 = buf[o + 2].toLong() and 0xff
            val b3 = buf[o + 3].toLong() and 0xff
            return if (littleEndian) (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
                   else (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }

        if (u16(header, 2) != 42) return null
        val firstIfdOffset = u32(header, 4)

        fun readAt(offset: Long, size: Int): ByteArray? {
            if (offset < 0 || size <= 0 || offset + size > fileLen) return null
            raf.seek(offset)
            val buf = ByteArray(size)
            raf.readFully(buf)
            return buf
        }

        data class Preview(val offset: Long, val length: Long, val area: Long)
        val previews = mutableListOf<Preview>()
        val toProcess = ArrayDeque<Long>().apply { add(firstIfdOffset) }
        val seen = HashSet<Long>()
        var ifdsExamined = 0
        var ifd0Orientation = ExifInterface.ORIENTATION_NORMAL

        val firstIfdSeen = firstIfdOffset
        while (toProcess.isNotEmpty() && ifdsExamined < 64) {
            val ifdOffset = toProcess.removeFirst()
            if (!seen.add(ifdOffset)) continue
            ifdsExamined++

            val countBuf = readAt(ifdOffset, 2) ?: continue
            val numEntries = u16(countBuf, 0)
            if (numEntries <= 0 || numEntries > 65535) continue
            val entriesSize = numEntries * 12
            val entriesBuf = readAt(ifdOffset + 2, entriesSize) ?: continue

            var width = 0
            var height = 0
            var compression = 0
            var stripOffsets = -1L
            var stripByteCounts = -1L
            var jpegOffset = -1L
            var jpegLength = -1L

            for (i in 0 until numEntries) {
                val base = i * 12
                val tag = u16(entriesBuf, base)
                val type = u16(entriesBuf, base + 2)
                val count = u32(entriesBuf, base + 4)
                val inlineOffset = base + 8

                fun readScalar(): Long = when (type) {
                    3 -> u16(entriesBuf, inlineOffset).toLong()
                    4 -> u32(entriesBuf, inlineOffset)
                    else -> u32(entriesBuf, inlineOffset)
                }

                when (tag) {
                    256 -> width = readScalar().toInt()        // ImageWidth
                    257 -> height = readScalar().toInt()       // ImageLength
                    259 -> compression = readScalar().toInt()  // Compression
                    273 -> stripOffsets = if (count == 1L) readScalar()
                                          else u32(entriesBuf, inlineOffset)
                    274 -> {                                   // Orientation (only trust IFD0's)
                        if (ifdOffset == firstIfdSeen) {
                            ifd0Orientation = readScalar().toInt()
                        }
                    }
                    279 -> stripByteCounts = if (count == 1L) readScalar()
                                             else u32(entriesBuf, inlineOffset)
                    330 -> {                                   // SubIFDs
                        if (count == 1L) {
                            toProcess.add(readScalar())
                        } else {
                            val arrayOffset = u32(entriesBuf, inlineOffset)
                            val cap = count.coerceAtMost(16L).toInt()
                            val subBuf = readAt(arrayOffset, cap * 4)
                            if (subBuf != null) {
                                for (j in 0 until cap) {
                                    val sub = u32(subBuf, j * 4)
                                    if (sub > 0) toProcess.add(sub)
                                }
                            }
                        }
                    }
                    513 -> jpegOffset = readScalar()           // JPEGInterchangeFormat
                    514 -> jpegLength = readScalar()           // JPEGInterchangeFormatLength
                }
            }

            val nextIfdBuf = readAt(ifdOffset + 2 + entriesSize, 4)
            if (nextIfdBuf != null) {
                val nextIfd = u32(nextIfdBuf, 0)
                if (nextIfd > 0) toProcess.add(nextIfd)
            }

            if (jpegOffset > 0 && jpegLength > 0 && jpegOffset + jpegLength <= fileLen) {
                previews.add(Preview(jpegOffset, jpegLength, width.toLong() * height.toLong()))
            } else if (compression == 7 && stripOffsets > 0 && stripByteCounts > 0 &&
                       stripOffsets + stripByteCounts <= fileLen) {
                previews.add(Preview(stripOffsets, stripByteCounts,
                    width.toLong() * height.toLong()))
            }
        }

        val best = previews.maxByOrNull { if (it.area > 0) it.area else it.length }
            ?: return null
        val jpegBytes = readAt(best.offset, best.length.toInt()) ?: return null
        return ParsedRawPreview(jpegBytes, ifd0Orientation)
    }
}

private fun adjustBitmapOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()

    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
    }

    return try {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: Exception) {
        e.printStackTrace()
        bitmap // Return the original bitmap if transformation fails
    }
}