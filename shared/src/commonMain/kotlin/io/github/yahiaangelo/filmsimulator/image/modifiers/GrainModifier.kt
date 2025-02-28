package io.github.yahiaangelo.filmsimulator.image.modifiers

import androidx.compose.ui.Modifier
import kotlin.random.Random

private val grainShader = """
    uniform float2 resolution;
    uniform shader content; 
    uniform float grain;
    uniform float time; // A changing value to animate grain

    float random(vec2 uv) {
        return fract(sin(dot(uv.xy, vec2(12.9898, 78.233))) * 43758.5453);
    }

    half4 main(vec2 fragCoord) {
        vec2 uv = fragCoord.xy / resolution.xy;
        half4 color = content.eval(fragCoord);
        // Add noise based on random function
        float noise = random(uv + time);
        color.rgb += (noise - 0.5) * grain;
        return color;
    }
""".trimIndent()

fun Modifier.grainShader(
    grain: Float,
    time: Float = Random.nextFloat(),
): Modifier =
    this then runtimeShader(grainShader) {
        uniform("grain", (grain / 10).coerceIn(0f, 1f))
        uniform("time", time) // Pass a changing time value for animated grain
    }
