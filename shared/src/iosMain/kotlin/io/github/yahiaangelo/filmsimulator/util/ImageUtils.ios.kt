package io.github.yahiaangelo.filmsimulator.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.seiko.imageloader.asImageBitmap
import io.github.yahiaangelo.filmsimulator.data.source.local.SettingsStorageImpl
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.refTo
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.CoreFoundation.CFDataCreate
import platform.CoreGraphics.CGColorRenderingIntent
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGImageCreate
import platform.CoreGraphics.CGPointMake
import platform.CoreImage.CIImage
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIGraphicsBeginImageContext
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImageOrientation
import platform.posix.memcpy
import util.IMAGE_FILE_NAME
import util.readImageFile
import util.saveImageFile
import kotlin.random.Random


/**
 * Main file for image processing
 */

/**
 * Adds Grain noise to an image
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun addGrain(
    imageBitmap: ImageBitmap,
    intensity: Int
): ImageBitmap {

    return withContext(Dispatchers.IO) {
        memScoped {
            val bitmap = imageBitmap.asSkiaBitmap().makeClone()
            val bytesPerPixel = 4 // Assuming RGBA
            val pixels = bitmap.readPixels(dstInfo = bitmap.imageInfo, dstRowBytes = bitmap.rowBytes, 0 ,0)!!

            // Apply noise to the RGB components of each pixel
            for (i in pixels.indices step bytesPerPixel) {
                // Skip i as it is the alpha channel
                for (j in 1..3) { // Only modify R, G, B components
                    val originalValue = pixels[i + j].toInt() and 0xFF
                    val noise = Random.nextInt(-intensity, intensity + 1)
                    val newValue = (originalValue + noise).coerceIn(0, 255).toByte()
                    pixels[i + j] = newValue
                }
            }

            // Write the modified pixels back to the bitmap.
            bitmap.installPixels(info = bitmap.imageInfo, pixels = pixels, rowBytes =  bitmap.rowBytes)


            bitmap.asImageBitmap()
        }
    }


}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toCIImage(w: Int, h: Int): CIImage {
    val bytesPerPixel = 4 // Assuming RGBA
    val bitsPerComponent = 8  // (8 bits per each channel)
    val bitsPerPixel = bytesPerPixel * bitsPerComponent
    val bytesPerRow = w * bytesPerPixel


    memScoped {
        val cfData = CFDataCreate(null, this@toCIImage.toNativeUByteArray(), w * h * bytesPerPixel.toLong())
        val cgDataProvider = CGDataProviderCreateWithCFData(data = cfData)

        val deviceColorSpace = CGColorSpaceCreateDeviceRGB()

        val image = CGImageCreate(
            width = w.toULong(),
            height = h.toULong(),
            bitsPerComponent = bitsPerComponent.toULong(),
            bitsPerPixel = bitsPerPixel.toULong(),
            bytesPerRow = bytesPerRow.toULong(),
            space = deviceColorSpace,
            bitmapInfo = 0.toUInt(),
            provider = cgDataProvider,
            decode = null,
            shouldInterpolate = true,
            intent = CGColorRenderingIntent.kCGRenderingIntentDefault
        )
        return CIImage(cGImage = image)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNativeUByteArray(): CPointer<UByteVar> {
    // Allocate native memory for the array. Note: This memory must be freed later to avoid memory leaks.
    val nativeArray = nativeHeap.allocArray<UByteVar>(size)

    // Copy each byte from the Kotlin ByteArray to the native array.
    forEachIndexed { index, byte ->
        nativeArray[index] = byte.toUByte()
    }

    return nativeArray
}

actual suspend fun ImageBitmap.readPixels(): ByteArray {
    val settings = SettingsStorageImpl()
    return withContext(Dispatchers.Main) {
        val bitmap = this@readPixels.asSkiaBitmap()
        val data = Image.makeFromBitmap(bitmap).encodeToData(format = EncodedImageFormat.JPEG, quality = settings.exportQuality)
        data!!.bytes
    }

}

@OptIn(ExperimentalForeignApi::class)
fun ByteArray.toUIImage(): UIImage? {
    val nsData = this.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), this.size.toULong())
    }
    return UIImage.imageWithData(nsData)
}

actual suspend fun fixImageOrientation(image: String): String {
    return withContext(Dispatchers.IO) {

        val imageByteArray = readImageFile(IMAGE_FILE_NAME)
        val fixedImage = imageByteArray.toUIImage()?.fixImageOrientation()?.toByteArray(1.0)
        fixedImage?.let {
            saveImageFile(IMAGE_FILE_NAME, it)
        }
        image
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun UIImage.fixImageOrientation(): UIImage {
    if (this.imageOrientation == UIImageOrientation.UIImageOrientationUp) return this

    UIGraphicsBeginImageContext(size = this.size)
    this.drawAtPoint(CGPointMake(0.0, 0.0))
    val fixedImage = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return fixedImage ?: this
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toByteArray(compressionQuality: Double): ByteArray {
    val validCompressionQuality = compressionQuality.coerceIn(0.0, 1.0)
    val jpegData = UIImageJPEGRepresentation(this, validCompressionQuality)!!
    return ByteArray(jpegData.length.toInt()).apply {
        memcpy(this.refTo(0), jpegData.bytes, jpegData.length)
    }
}