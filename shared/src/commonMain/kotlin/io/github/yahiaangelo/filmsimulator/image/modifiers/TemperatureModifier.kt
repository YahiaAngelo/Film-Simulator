package io.github.yahiaangelo.filmsimulator.image.modifiers

import androidx.compose.ui.Modifier

private val temperatureShader = """
    uniform float2 resolution;
    uniform shader content; 
    uniform float temperature;

    half4 main(vec2 fragCoord) {
        vec2 uv = fragCoord.xy / resolution.xy;
        half4 color = content.eval(fragCoord);
        // Apply temperature shift (more red for warmth, more blue for coolness)
        color.r += temperature * 0.1;
        color.b -= temperature * 0.1;
        return color;
    }
""".trimIndent()

fun Modifier.temperatureShader(
    temperature: Float,
): Modifier =
    this then runtimeShader(temperatureShader) {
        uniform("temperature", (temperature/10).coerceIn(-1f, 1f))
    }
