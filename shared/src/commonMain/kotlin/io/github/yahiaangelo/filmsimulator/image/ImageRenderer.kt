package io.github.yahiaangelo.filmsimulator.image

import androidx.compose.ui.graphics.ImageBitmap
import io.github.yahiaangelo.filmsimulator.image.modifiers.ShaderUniformProvider
import io.github.yahiaangelo.filmsimulator.util.readPixels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface

/**
 * Common interface for rendering and exporting images with shaders
 */
class ImageRenderer {
    /**
     * Renders a bitmap with the given shader applied and returns the resulting ImageBitmap
     *
     * @param sourceBitmap The source image to apply the shader to
     * @param width The width of the resulting image
     * @param height The height of the resulting image
     * @param shaderString The AGSL/SkSL shader code as string
     * @param isRuntimeShader Whether to use runtime shader (for shaders that need the source image)
     * @param uniformName The uniform name for binding the source image (for runtime shaders)
     * @param uniformsBlock Optional block to set custom uniforms
     * @return The processed ImageBitmap
     */
    suspend fun renderToImageBitmap(
        sourceBitmap: ImageBitmap,
        width: Int,
        height: Int,
        quality: Int,
        shaderString: String,
        isRuntimeShader: Boolean = false,
        uniformName: String = "content",
        uniformsBlock: (ShaderUniformProvider.() -> Unit)? = null
    ): ImageBitmap?  = withContext(Dispatchers.Default) {
        // Create Skia bitmap and surface to draw on
        val skiaImageInfo = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        val surface = Surface.makeRaster(skiaImageInfo)
        val canvas = surface.canvas

        // Create RuntimeEffect from shader
        val runtimeEffect = RuntimeEffect.makeForShader(shaderString)
        val runtimeShaderBuilder = RuntimeShaderBuilder(runtimeEffect)

        // Setup the shader uniforms
        val shaderUniformProvider = object : ShaderUniformProvider {
            override fun uniform(name: String, value: Int) {
                runtimeShaderBuilder.uniform(name, value)
            }

            override fun uniform(name: String, value: Float) {
                runtimeShaderBuilder.uniform(name, value)
            }

            override fun uniform(name: String, value1: Float, value2: Float) {
                runtimeShaderBuilder.uniform(name, value1, value2)
            }
        }

        // Set resolution uniform
        shaderUniformProvider.uniform("resolution", width.toFloat(), height.toFloat())

        // Apply custom uniforms
        uniformsBlock?.invoke(shaderUniformProvider)

        // Convert ImageBitmap to Skia image
        val nativeSourceImage = Image.makeFromEncoded(sourceBitmap.readPixels())

        // For runtime shaders, set the source image as a shader child
        if (isRuntimeShader) {
            runtimeShaderBuilder.child(uniformName, nativeSourceImage.makeShader())
        }

        // Create paint with shader
        val paint = Paint()
        paint.shader = runtimeShaderBuilder.makeShader()

        // Draw with the shader
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), paint)

        // Get the result as ImageBitmap
        val resultImage = surface.makeImageSnapshot()
        resultImage.encodeToData(quality = quality)?.bytes?.decodeToImageBitmap()
    }

    private fun Image.asSkiaBitmap(): Bitmap {
        val bitmap = Bitmap()
        this.readPixels(bitmap)
        return bitmap
    }

}