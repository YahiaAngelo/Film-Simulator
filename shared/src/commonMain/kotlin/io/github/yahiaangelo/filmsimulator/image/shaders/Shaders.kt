package io.github.yahiaangelo.filmsimulator.image.shaders

import io.github.yahiaangelo.filmsimulator.image.ImageAdjustments
import io.github.yahiaangelo.filmsimulator.image.modifiers.ShaderUniformProvider

/**
 * Utility object to provide shader code for image adjustments
 */
object Shaders {
    /**
     * Main combined adjustment shader that applies all image effects
     */
    fun getImageAdjustmentShader(): String = """
        uniform float2 resolution;
        uniform shader content;
        
        // Contrast
        uniform float contrast;
        
        // Brightness
        uniform float brightness;
        
        // Saturation
        uniform float saturation;
        
        // Temperature
        uniform float temperature;
        
        // Exposure
        uniform float exposure;
        
        // Grain
        uniform float grain;
        uniform float time;
        
        // Chromatic aberration
        uniform float chromaticAberration;
        
        float random(vec2 uv) {
            return fract(sin(dot(uv.xy, vec2(12.9898, 78.233))) * 43758.5453);
        }
        
        half4 main(vec2 fragCoord) {
            vec2 uv = fragCoord.xy / resolution.xy;
            half4 color;
            
            // Apply chromatic aberration first if needed
            if (chromaticAberration > 0.0) {
                vec2 offset = chromaticAberration / resolution.xy;
                float r = content.eval(resolution.xy * ((uv - 0.5) * (1.0 + offset) + 0.5)).r;
                float g = content.eval(fragCoord).g;
                float b = content.eval(resolution.xy * ((uv - 0.5) * (1.0 - offset) + 0.5)).b;
                color = half4(r, g, b, content.eval(fragCoord).a);
            } else {
                color = content.eval(fragCoord);
            }
            
            // Apply exposure
            if (exposure != 0.0) {
                color.rgb *= exp2(exposure);
            }
            
            // Apply contrast with a better formula
            if (contrast != 0.0) {
                // Convert to a multiplier (1.0 is no change)
                float contrastFactor = contrast > 0.0 ? 
                    1.0 + contrast : 
                    1.0 / (1.0 - contrast);
                
                // Apply contrast by centering around 0.5
                color.rgb = (color.rgb - 0.5) * contrastFactor + 0.5;
            }
            
            // Apply brightness
            if (brightness != 0.0) {
                color.rgb += brightness;
            }
            
            // Apply saturation
            if (saturation != 0.0) {
                float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                color.rgb = mix(vec3(gray), color.rgb, 1.0 + saturation);
            }
            
            // Apply temperature
            if (temperature != 0.0) {
                color.r += temperature * 0.1;
                color.b -= temperature * 0.1;
            }
            
            // Apply grain last
            if (grain > 0.0) {
                float noise = random(uv + time);
                color.rgb += (noise - 0.5) * grain;
            }
            
            return color;
        }
    """.trimIndent()

    /**
     * Set uniform values for the image adjustment shader
     */
    fun ImageAdjustments.setShaderUniforms(
        provider: ShaderUniformProvider,
        useRandomTime: Boolean = false,
        timeValue: Float = 0f
    ) {
        with(provider) {
            uniform("contrast", (contrast / 10).coerceIn(-1f, 1f))
            uniform("brightness", (brightness / 20).coerceIn(-1f, 1f))
            uniform("saturation", (saturation / 20).coerceIn(-1f, 1f))
            uniform("temperature", (temperature / 20).coerceIn(-1f, 1f))
            uniform("exposure", (exposure / 20).coerceIn(-2f, 2f))
            uniform("grain", (grain / 10).coerceIn(0f, 1f))
            uniform("time", if (useRandomTime) kotlin.random.Random.nextFloat() else timeValue)
            uniform("chromaticAberration", chromaticAberration)
        }
    }
}