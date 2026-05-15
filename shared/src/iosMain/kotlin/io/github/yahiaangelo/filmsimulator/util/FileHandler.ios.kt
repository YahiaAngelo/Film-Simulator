package util

import io.github.yahiaangelo.filmsimulator.screens.settings.ExportFormat
import io.github.yahiaangelo.filmsimulator.util.AppContext
import io.github.yahiaangelo.filmsimulator.util.toUIImage
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.CoreFoundation.CFDictionaryCreateMutableCopy
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFURLRef
import platform.CoreFoundation.kCFNumberIntType
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSTemporaryDirectory
import platform.ImageIO.CGImageDestinationAddImageFromSource
import platform.ImageIO.CGImageDestinationCreateWithURL
import platform.ImageIO.CGImageDestinationFinalize
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.CGImageSourceGetType
import platform.ImageIO.kCGImagePropertyOrientation
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIImageWriteToSavedPhotosAlbum
import kotlin.coroutines.resume

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

@OptIn(ExperimentalForeignApi::class)
actual suspend fun saveImageToGallery(
    image: String,
    appContext: AppContext,
    format: ExportFormat,
    originalImage: String?,
) {
    if (format == ExportFormat.ORIGINAL && originalImage != null) {
        val ok = runCatching {
            saveImageWithOriginalFormatAndMetadata(image, originalImage)
        }.getOrDefault(false)
        if (ok) return
        // Fall through to plain JPEG path on failure.
    }

    val uiImage = readImageFile(image).toUIImage()!!
    UIImageWriteToSavedPhotosAlbum(uiImage, null, null, null)
}

/**
 * Re-encode the processed image into the original file's UTI (HEIC/JPEG/PNG/…) with
 * its EXIF/GPS/TIFF metadata embedded, then import the resulting file into Photos
 * via PhotoKit so the metadata survives.
 *
 * Implementation uses file-URL-based ImageIO so we work with toll-free-bridged
 * NSURL ↔ CFURLRef via [CFBridgingRetain]. Returns false on any failure so the
 * caller can fall back to a plain JPEG save.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Suppress("UNCHECKED_CAST")
private suspend fun saveImageWithOriginalFormatAndMetadata(
    processedImageFile: String,
    originalImageFile: String,
): Boolean = withContext(Dispatchers.IO) {
    val originalPath = "${systemTemporaryPath / originalImageFile}"
    val processedPath = "${systemTemporaryPath / processedImageFile}"

    val originalUrl = NSURL.fileURLWithPath(originalPath)
    val processedUrl = NSURL.fileURLWithPath(processedPath)

    // NSURL → CFURLRef. CFBridgingRetain transfers ownership; we balance with CFRelease.
    val originalCfUrl = CFBridgingRetain(originalUrl) as CFURLRef? ?: return@withContext false
    val processedCfUrl = CFBridgingRetain(processedUrl) as CFURLRef?
        ?: run { CFRelease(originalCfUrl); return@withContext false }

    var destinationTmpPath: String? = null
    var mergedProps: CFMutableDictionaryRef? = null

    try {
        val originalSource = CGImageSourceCreateWithURL(originalCfUrl, null)
            ?: return@withContext false
        val sourceUti = CGImageSourceGetType(originalSource) ?: return@withContext false

        val processedSource = CGImageSourceCreateWithURL(processedCfUrl, null)
            ?: return@withContext false

        // Make a mutable copy of the source's properties and force Orientation = 1
        // because processed pixels are already in display orientation.
        val sourceProps = CGImageSourceCopyPropertiesAtIndex(originalSource, 0u, null)
            ?: return@withContext false
        mergedProps = CFDictionaryCreateMutableCopy(null, 0, sourceProps)
            ?: return@withContext false
        memScoped {
            val one = alloc<IntVar>().apply { value = 1 }
            val orientationNum = CFNumberCreate(null, kCFNumberIntType, one.ptr)
            CFDictionarySetValue(mergedProps, kCGImagePropertyOrientation, orientationNum)
            CFRelease(orientationNum)
        }

        val ext = extensionForExtension(
            originalImageFile.substringAfterLast('.', "jpg").lowercase()
        )
        val tmpPath = NSTemporaryDirectory() + "export-${NSUUID().UUIDString}.$ext"
        destinationTmpPath = tmpPath
        val tmpUrl = NSURL.fileURLWithPath(tmpPath)
        val tmpCfUrl = CFBridgingRetain(tmpUrl) as CFURLRef? ?: return@withContext false

        val ok = try {
            val destination = CGImageDestinationCreateWithURL(tmpCfUrl, sourceUti, 1u, null)
                ?: return@withContext false
            CGImageDestinationAddImageFromSource(destination, processedSource, 0u, mergedProps)
            CGImageDestinationFinalize(destination)
        } finally {
            CFRelease(tmpCfUrl)
        }
        if (!ok) return@withContext false

        suspendCancellableCoroutine<Boolean> { continuation ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                val request = PHAssetCreationRequest.creationRequestForAsset()
                request.addResourceWithType(
                    PHAssetResourceTypePhoto,
                    tmpUrl,
                    null,
                )
            }, completionHandler = { success, _ ->
                runCatching { FileSystem.SYSTEM.delete(tmpPath.toPath()) }
                destinationTmpPath = null
                continuation.resume(success)
            })
        }
    } finally {
        CFRelease(originalCfUrl)
        CFRelease(processedCfUrl)
        if (mergedProps != null) CFRelease(mergedProps)
        destinationTmpPath?.let { runCatching { FileSystem.SYSTEM.delete(it.toPath()) } }
    }
}

private fun extensionForExtension(srcExt: String): String = when (srcExt) {
    "heic" -> "heic"
    "heif" -> "heif"
    "png" -> "png"
    "webp" -> "webp"
    "tiff", "tif" -> "tiff"
    "jpg", "jpeg" -> "jpg"
    else -> "jpg"
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