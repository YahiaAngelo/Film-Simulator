package io.github.yahiaangelo.filmsimulator.util

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.yahiaangelo.filmsimulator.bridge.MetalLUTProcessor
import util.systemTemporaryPath

/**
 * iOS implementation of NativeImageAdjustmentProcessor
 * Uses MetalLUTProcessor with Core Graphics for image adjustments
 */
@OptIn(ExperimentalForeignApi::class)
actual class NativeImageAdjustmentProcessor {

    private val processor: MetalLUTProcessor by lazy {
        MetalLUTProcessor()
    }

    /**
     * Apply image adjustments to an image file
     */
    actual suspend fun applyAdjustments(
        inputPath: String,
        outputPath: String,
        adjustments: NativeImageAdjustments,
        forExport: Boolean
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            // Construct full paths if only filenames are provided
            val fullInputPath = if (inputPath.startsWith("/")) inputPath else "$systemTemporaryPath/$inputPath"
            val fullOutputPath = if (outputPath.startsWith("/")) outputPath else "$systemTemporaryPath/$outputPath"

            processor.applyAdjustmentsWithInputPath(
                fullInputPath,
                outputPath = fullOutputPath,
                contrast = adjustments.contrast,
                brightness = adjustments.brightness,
                saturation = adjustments.saturation,
                temperature = adjustments.temperature,
                exposure = adjustments.exposure,
                grain = adjustments.grain,
                chromaticAberration = adjustments.chromaticAberration,
                forExport = forExport
            )
        } catch (e: Exception) {
            println("Error applying adjustments on iOS: ${e.message}")
            false
        }
    }

    /**
     * Apply both LUT and image adjustments in a single pass
     */
    actual suspend fun applyLutAndAdjustments(
        inputPath: String,
        outputPath: String,
        lutPath: String,
        adjustments: NativeImageAdjustments,
        createThumbnail: Boolean
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            // Construct full paths if only filenames are provided
            val fullInputPath = if (inputPath.startsWith("/")) inputPath else "$systemTemporaryPath/$inputPath"
            val fullOutputPath = if (outputPath.startsWith("/")) outputPath else "$systemTemporaryPath/$outputPath"
            val fullLutPath = if (lutPath.startsWith("/")) lutPath else "$systemTemporaryPath/$lutPath"

            processor.applyLUTAndAdjustmentsWithInputPath(
                fullInputPath,
                outputPath = fullOutputPath,
                lutPath = fullLutPath,
                contrast = adjustments.contrast,
                brightness = adjustments.brightness,
                saturation = adjustments.saturation,
                temperature = adjustments.temperature,
                exposure = adjustments.exposure,
                grain = adjustments.grain,
                chromaticAberration = adjustments.chromaticAberration,
                createThumbnail = createThumbnail
            )
        } catch (e: Exception) {
            println("Error applying LUT and adjustments on iOS: ${e.message}")
            false
        }
    }

    /**
     * Check if native processing is available on this platform
     */
    actual fun isAvailable(): Boolean = true
}