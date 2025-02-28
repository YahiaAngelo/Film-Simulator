package io.github.yahiaangelo.filmsimulator.image.modifiers

import androidx.compose.ui.Modifier

private val saturationShader = """
    uniform float2 resolution;
    uniform shader content; 
    uniform float saturation;

    half4 main(vec2 fragCoord) {
        vec2 uv = fragCoord.xy / resolution.xy;
        half4 color = content.eval(fragCoord);
        // Convert to grayscale (luminosity method)
        float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        // Blend between grayscale and color based on saturation factor
        color.rgb = mix(vec3(gray), color.rgb, 1.0 + saturation);
        return color;
    }
""".trimIndent()

fun Modifier.saturationShader(
    saturation: Float,
): Modifier =
    this then runtimeShader(saturationShader) {
        uniform("saturation", (saturation/10).coerceIn(-1f, 1f))
    }
