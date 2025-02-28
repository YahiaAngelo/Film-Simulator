package io.github.yahiaangelo.filmsimulator.image.modifiers

import androidx.compose.ui.Modifier

private val brightnessShader = """
    uniform float2 resolution;
    uniform shader content; 
    uniform float brightness;

    half4 main(vec2 fragCoord) {
        vec2 uv = fragCoord.xy / resolution.xy;
        half4 color = content.eval(fragCoord);
        // Adjust brightness: brightness of 0 means no change, 1 is maximum brightness, -1 is completely dark
        color.rgb += brightness;
        return color;
    }
""".trimIndent()

fun Modifier.brightnessShader(
    brightness: Float,
): Modifier =
    this then runtimeShader(brightnessShader) {
        uniform("brightness", (brightness / 10).coerceIn(-1f, 1f))
    }
