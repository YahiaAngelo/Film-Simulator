package io.github.yahiaangelo.filmsimulator.image.modifiers

import androidx.compose.ui.Modifier

private val shader = """
    uniform float2 resolution;
    uniform shader content; 
    uniform float intensity;

    half4 main(vec2 fragCoord) {
        vec2 uv = fragCoord.xy / resolution.xy;
        half4 color = content.eval(fragCoord);
        // Compute contrast factor: -10 -> 0.5, 0 -> 1, 10 -> 1.5
        float contrast = 1.0 + (intensity / 20.0);
        // Apply contrast adjustment
        color.rgb = mix(vec3(0.5), color.rgb, contrast);
        return color;
    }
""".trimIndent()

fun Modifier.contrastShader(
    intensity: Float,
): Modifier =
    this then runtimeShader(shader) {
        uniform("intensity", intensity.coerceIn(-10f, 10f))
    }
