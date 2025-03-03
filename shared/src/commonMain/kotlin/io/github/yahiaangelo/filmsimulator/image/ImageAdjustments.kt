package io.github.yahiaangelo.filmsimulator.image

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import io.github.yahiaangelo.filmsimulator.image.modifiers.*
import io.github.yahiaangelo.filmsimulator.image.export.ShaderExporter
import io.github.yahiaangelo.filmsimulator.image.shaders.Shaders
import io.github.yahiaangelo.filmsimulator.image.shaders.Shaders.setShaderUniforms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Data class that holds all image adjustment values
 */
data class ImageAdjustments(
    val contrast: Float = 0f,
    val brightness: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val exposure: Float = 0f,
    val grain: Float = 0f,
    val chromaticAberration: Float = 0f
) {

    /**
     * Check if all adjustments are at their default values
     */
    fun isDefault(): Boolean {
        return contrast == 0f &&
                brightness == 0f &&
                saturation == 0f &&
                temperature == 0f &&
                exposure == 0f &&
                grain == 0f &&
                chromaticAberration == 0f
    }

    /**
     * Check if any adjustments are applied
     */
    fun hasAdjustments(): Boolean {
        return contrast != 0f || brightness != 0f || saturation != 0f ||
                temperature != 0f || exposure != 0f || grain != 0f || chromaticAberration != 0f
    }

    /**
     * Apply all adjustments to the source image and save to file
     */
    suspend fun applyToImageAndSave(
        sourceBitmap: ImageBitmap,
        outputPath: String,
        quality: Int = 90
    ): Boolean {
        val shaderExporter = ShaderExporter()
        return shaderExporter.applyAdjustmentsAndSave(
            sourceBitmap = sourceBitmap,
            outputPath = outputPath,
            adjustments = this,
            quality = quality
        )
    }
}

/**
 * A composable wrapper that wraps the image content to isolate the shader effects
 * This prevents the shaders from affecting the entire view
 */
@Composable
fun ImageWithAdjustments(
    adjustments: ImageAdjustments,
    modifier: Modifier = Modifier,
    applyModifiers: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        // Only apply the shader if explicitly requested
        if (applyModifiers && adjustments.hasAdjustments()) {
            // Apply all adjustments in a single runtime shader
            val combinedShaderModifier = Modifier.runtimeShader(
                shader = Shaders.getImageAdjustmentShader(),
                uniformsBlock = {
                    adjustments.setShaderUniforms(
                        provider = this,
                        timeValue = (Clock.System.now().toEpochMilliseconds() % 10000) / 10000f
                    )
                }
            )

            Box(modifier = combinedShaderModifier) {
                content()
            }
        } else {
            content()
        }
    }
}