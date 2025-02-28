package io.github.yahiaangelo.filmsimulator.image

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.yahiaangelo.filmsimulator.image.modifiers.*

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
)

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
        if (applyModifiers) {
            // Create a chain of modifiers for each effect
            // Start with a base modifier
            var imageModifier: Modifier = Modifier

            // Apply each effect if its value is not zero
            if (adjustments.contrast != 0f) {
                imageModifier = imageModifier.contrastShader(adjustments.contrast)
            }

            if (adjustments.brightness != 0f) {
                imageModifier = imageModifier.brightnessShader(adjustments.brightness)
            }

            if (adjustments.saturation != 0f) {
                imageModifier = imageModifier.saturationShader(adjustments.saturation)
            }

            if (adjustments.temperature != 0f) {
                imageModifier = imageModifier.temperatureShader(adjustments.temperature)
            }

            if (adjustments.exposure != 0f) {
                imageModifier = imageModifier.exposureShader(adjustments.exposure)
            }

            if (adjustments.grain != 0f) {
                imageModifier = imageModifier.grainShader(adjustments.grain)
            }

            if (adjustments.chromaticAberration != 0f) {
                imageModifier = imageModifier.chromaticAberrationShader(adjustments.chromaticAberration)
            }

            Box(modifier = imageModifier) {
                content()
            }
        } else {
            content()
        }
    }
}