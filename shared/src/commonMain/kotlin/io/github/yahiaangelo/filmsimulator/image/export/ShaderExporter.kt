package io.github.yahiaangelo.filmsimulator.image.export

import androidx.compose.ui.graphics.ImageBitmap
import io.github.yahiaangelo.filmsimulator.image.ImageAdjustments
import io.github.yahiaangelo.filmsimulator.image.ImageRenderer
import io.github.yahiaangelo.filmsimulator.image.shaders.Shaders
import io.github.yahiaangelo.filmsimulator.image.shaders.Shaders.setShaderUniforms
import io.github.yahiaangelo.filmsimulator.util.readPixels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import util.saveImageFile

/**
 * Utility class to apply shaders to images and export them
 */
class ShaderExporter {
    private val imageRenderer = ImageRenderer()

    /**
     * Apply all adjustments from ImageAdjustments to an image and return the processed ImageBitmap
     *
     * @param sourceBitmap The original image to process
     * @param adjustments The image adjustments to apply
     * @return The processed ImageBitmap
     */
    suspend fun applyAdjustments(
        sourceBitmap: ImageBitmap,
        adjustments: ImageAdjustments,
        quality: Int
    ): ImageBitmap? {
        // Skip processing if no adjustments need to be applied
        if (adjustments.isDefault()) {
            return sourceBitmap
        }

        return imageRenderer.renderToImageBitmap(
            sourceBitmap = sourceBitmap,
            width = sourceBitmap.width,
            height = sourceBitmap.height,
            quality = quality,
            shaderString = Shaders.getImageAdjustmentShader(),
            isRuntimeShader = true,
            uniformsBlock = {
                // Set all uniforms at once with the utility function
                adjustments.setShaderUniforms(provider = this, useRandomTime = true)
            }
        )
    }


    /**
     * Apply all adjustments to an image and save it to a file
     *
     * @param sourceBitmap The original image to process
     * @param outputPath The path to save the processed image to
     * @param adjustments The image adjustments to apply
     * @param quality The quality of the output image (for JPEG/WebP)
     * @return true if successful, false otherwise
     */
    suspend fun applyAdjustmentsAndSave(
        sourceBitmap: ImageBitmap,
        outputPath: String,
        adjustments: ImageAdjustments,
        quality: Int = 90
    ): Boolean = withContext(Dispatchers.Default) {
        val processedBitmap = applyAdjustments(
            sourceBitmap = sourceBitmap,
            adjustments = adjustments,
            quality = quality
        )
        if (processedBitmap == null) {
            return@withContext false
        }
        saveImageFile(outputPath, processedBitmap.readPixels())
        true
    }
}