package util

import io.github.yahiaangelo.filmsimulator.screens.settings.ExportFormat
import io.github.yahiaangelo.filmsimulator.util.AppContext
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
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFURLRef
import platform.CoreFoundation.kCFNumberIntType
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSDate
import platform.Foundation.NSMutableArray
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
import platform.Photos.PHAssetCollection
import platform.Photos.PHAssetCollectionChangeRequest
import platform.Photos.PHAssetCollectionSubtypeAlbumRegular
import platform.Photos.PHAssetCollectionTypeAlbum
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHObjectPlaceholder
import platform.Photos.PHPhotoLibrary
import kotlin.coroutines.resume

private const val ALBUM_TITLE = "Film Simulator"

// File extensions for camera RAW formats. ImageIO can read most of these but
// generally can't write them, and even where it can, re-encoding processed
// sRGB pixels as RAW is meaningless — the RAW container is for unprocessed
// sensor data. For these inputs the export is downgraded to JPEG while still
// carrying the original EXIF/GPS/TIFF dictionary so capture metadata survives.
private val RAW_EXTENSIONS = setOf(
    "dng", "raw", "nef", "cr2", "cr3", "arw", "rw2", "raf", "orf", "pef", "srw", "x3f",
)

private const val JPEG_UTI = "public.jpeg"

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

    // Plain path: import the already-encoded processed JPEG straight into the
    // app album via PhotoKit. Going through PhotoKit (rather than the older
    // UIImageWriteToSavedPhotosAlbum) is what lets us place the asset inside a
    // named album rather than dumping it into the camera roll.
    val processedPath = "${systemTemporaryPath / image}"
    val processedUrl = NSURL.fileURLWithPath(processedPath)
    saveAssetToAppAlbum(processedUrl)
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
    var ownedDestUti: CFStringRef? = null

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

        // For RAW sources we downgrade the container to JPEG (sourceUti would
        // be e.g. com.adobe.dng, which CGImageDestination can't write — and
        // wouldn't be meaningful anyway since we have processed sRGB pixels).
        // mergedProps still carries the original EXIF dictionary, which ImageIO
        // happily writes into the JPEG APP1 segment.
        val srcExt = originalImageFile.substringAfterLast('.', "jpg").lowercase()
        val isRaw = srcExt in RAW_EXTENSIONS
        val ext = if (isRaw) "jpg" else extensionForExtension(srcExt)
        val destUti: CFStringRef = if (isRaw) {
            // CFBridgingRetain returns +1 — track in ownedDestUti so the
            // outer finally releases it. sourceUti is borrowed, no release.
            // K/N implicitly bridges Kotlin String to NSString at the call site.
            val owned = CFBridgingRetain(JPEG_UTI) as CFStringRef?
                ?: return@withContext false
            ownedDestUti = owned
            owned
        } else {
            sourceUti
        }

        val tmpPath = NSTemporaryDirectory() + "export-${NSUUID().UUIDString}.$ext"
        destinationTmpPath = tmpPath
        val tmpUrl = NSURL.fileURLWithPath(tmpPath)
        val tmpCfUrl = CFBridgingRetain(tmpUrl) as CFURLRef? ?: return@withContext false

        val ok = try {
            val destination = CGImageDestinationCreateWithURL(tmpCfUrl, destUti, 1u, null)
                ?: return@withContext false
            CGImageDestinationAddImageFromSource(destination, processedSource, 0u, mergedProps)
            CGImageDestinationFinalize(destination)
        } finally {
            CFRelease(tmpCfUrl)
        }
        if (!ok) return@withContext false

        suspendCancellableCoroutine<Boolean> { continuation ->
            // Look up the album outside performChanges — the fetch is a read and
            // performChanges is the write transaction. Inside the block we either
            // mutate the existing album or create a new one.
            val existingAlbum = findAppAlbum()
            PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                val request = PHAssetCreationRequest.creationRequestForAsset()
                request.addResourceWithType(
                    PHAssetResourceTypePhoto,
                    tmpUrl,
                    null,
                )
                // EXIF DateTimeOriginal stays intact inside the file (archival
                // metadata) — but the PHAsset's creationDate is what Photos.app
                // uses for sorting, so we set it to "now" so exports surface at
                // the top of Recents and the app album.
                request.setCreationDate(NSDate())
                addAssetToAppAlbum(request.placeholderForCreatedAsset(), existingAlbum)
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
        if (ownedDestUti != null) CFRelease(ownedDestUti)
        destinationTmpPath?.let { runCatching { FileSystem.SYSTEM.delete(it.toPath()) } }
    }
}

/**
 * Save a file at [fileUrl] as a new asset in the app's Photos album,
 * creating the album if it doesn't exist yet.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun saveAssetToAppAlbum(fileUrl: NSURL): Boolean =
    suspendCancellableCoroutine { continuation ->
        val existingAlbum = findAppAlbum()
        PHPhotoLibrary.sharedPhotoLibrary().performChanges({
            val request = PHAssetCreationRequest.creationRequestForAsset()
            request.addResourceWithType(PHAssetResourceTypePhoto, fileUrl, null)
            // Stamp the asset's sort-time as "now" so the export lands at the
            // top of Recents; the embedded EXIF capture date is left untouched.
            request.setCreationDate(NSDate())
            addAssetToAppAlbum(request.placeholderForCreatedAsset(), existingAlbum)
        }, completionHandler = { success, _ ->
            continuation.resume(success)
        })
    }

/**
 * Inside a [PHPhotoLibrary.performChanges] block, add the just-created asset
 * (represented by [placeholder]) to the app album. If [existingAlbum] is null
 * a new album with [ALBUM_TITLE] is created in the same transaction.
 */
@OptIn(ExperimentalForeignApi::class)
private fun addAssetToAppAlbum(
    placeholder: PHObjectPlaceholder?,
    existingAlbum: PHAssetCollection?,
) {
    if (placeholder == null) return
    val albumRequest = if (existingAlbum != null) {
        PHAssetCollectionChangeRequest.changeRequestForAssetCollection(existingAlbum)
    } else {
        PHAssetCollectionChangeRequest.creationRequestForAssetCollectionWithTitle(ALBUM_TITLE)
    }
    // PHAssetCollectionChangeRequest.addAssets expects an NSFastEnumeration —
    // NSMutableArray adopts it. We wrap the single placeholder so the call works
    // for the common one-asset-at-a-time export flow.
    val assets = NSMutableArray()
    assets.addObject(placeholder)
    albumRequest?.addAssets(assets)
}

private fun findAppAlbum(): PHAssetCollection? {
    val result = PHAssetCollection.fetchAssetCollectionsWithType(
        PHAssetCollectionTypeAlbum,
        PHAssetCollectionSubtypeAlbumRegular,
        null,
    )
    val count = result.count.toInt()
    for (i in 0 until count) {
        val collection = result.objectAtIndex(i.toULong()) as? PHAssetCollection
        if (collection?.localizedTitle == ALBUM_TITLE) return collection
    }
    return null
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