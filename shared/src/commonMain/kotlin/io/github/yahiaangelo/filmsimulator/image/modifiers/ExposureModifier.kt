package io.github.yahiaangelo.filmsimulator.image.modifiers

import androidx.compose.ui.Modifier

private val exposureShader = """
    uniform float2 resolution;
    uniform shader content; 
    uniform float exposure;

    half4 main(vec2 fragCoord) {
        vec2 uv = fragCoord.xy / resolution.xy;
        half4 color = content.eval(fragCoord);
        // Apply exposure adjustment (power function)
        color.rgb *= exp2(exposure);
        return color;
    }
""".trimIndent()

fun Modifier.exposureShader(
    exposure: Float,
): Modifier =
    this then runtimeShader(exposureShader) {
        uniform("exposure", (exposure / 10).coerceIn(-2f, 2f))
    }
