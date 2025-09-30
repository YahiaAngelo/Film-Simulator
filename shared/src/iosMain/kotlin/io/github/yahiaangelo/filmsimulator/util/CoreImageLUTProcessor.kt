package io.github.yahiaangelo.filmsimulator.util

import kotlinx.cinterop.*
import platform.UIKit.*
import platform.Foundation.*
import platform.CoreImage.*
import platform.CoreGraphics.*
import platform.posix.*
import kotlin.math.*

/**
 * GPU-optimized LUT processor using Core Image filters
 * Mirrors the Android native implementation while leveraging iOS GPU capabilities
 */
@OptIn(ExperimentalForeignApi::class)
class CoreImageLUTProcessor {

    private val ciContext: CIContext by lazy {
        // Create CIContext with GPU acceleration
        // Core Image will automatically use the best available rendering option (GPU when available)
        CIContext()
    }

    data class LUT3D(
        val size: Int,
        val data: FloatArray
    )

    /**
     * Apply 3D LUT to an image using GPU-accelerated Core Image filters
     */
    suspend fun applyLUT(
        inputPath: String,
        outputPath: String,
        lutPath: String,
        createThumbnail: Boolean = false
    ): Boolean {
        return try {
            // Load input image
            val inputImage = UIImage.imageWithContentsOfFile(inputPath) ?: run {
                println("Failed to load input image from: $inputPath")
                return false
            }

            // Parse LUT file
            val lut = parseCubeLUT(lutPath) ?: run {
                println("Failed to parse LUT file: $lutPath")
                return false
            }

            // Process image with GPU acceleration
            val outputImage = processImageWithGPU(inputImage, lut, createThumbnail) ?: run {
                println("GPU image processing failed")
                return false
            }

            // Save output image
            val imageData = UIImageJPEGRepresentation(outputImage, 0.95) ?: run {
                println("Failed to create JPEG data")
                return false
            }

            imageData.writeToFile(outputPath, atomically = true)
        } catch (e: Exception) {
            println("Error in applyLUT: ${e.message}")
            false
        }
    }

    /**
     * Add film grain effect using GPU-accelerated CIFilter
     */
    suspend fun addGrain(
        inputPath: String,
        outputPath: String,
        intensity: Float
    ): Boolean {
        return try {
            val inputImage = UIImage.imageWithContentsOfFile(inputPath) ?: return false
            val outputImage = processGrainWithGPU(inputImage, intensity) ?: return false

            val imageData = UIImageJPEGRepresentation(outputImage, 0.95) ?: return false
            imageData.writeToFile(outputPath, atomically = true)
        } catch (e: Exception) {
            println("Error adding grain: ${e.message}")
            false
        }
    }

    /**
     * Process image using manual trilinear interpolation matching Android's implementation exactly
     */
    private fun processImageWithGPU(
        inputImage: UIImage,
        lut: LUT3D,
        createThumbnail: Boolean
    ): UIImage? {
        // Get input dimensions
        val inputWidth = inputImage.size.useContents { width.toInt() }
        val inputHeight = inputImage.size.useContents { height.toInt() }

        // Calculate output dimensions
        val (outputWidth, outputHeight) = if (createThumbnail) {
            val thumbnailWidth = min(320, inputWidth)
            val thumbnailHeight = (thumbnailWidth.toFloat() * inputHeight / inputWidth).toInt()
            thumbnailWidth to thumbnailHeight
        } else {
            inputWidth to inputHeight
        }

        // Process image using manual pixel manipulation to match Android exactly
        val cgImage = inputImage.CGImage ?: return null
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val bytesPerPixel = 4
        val bytesPerRow = outputWidth * bytesPerPixel
        val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big

        return memScoped {
            val rawData = allocArray<UByteVar>(outputHeight * bytesPerRow)

            val context = CGBitmapContextCreate(
                rawData,
                outputWidth.toULong(),
                outputHeight.toULong(),
                8.toULong(),
                bytesPerRow.toULong(),
                colorSpace,
                bitmapInfo.toUInt()
            ) ?: return null

            val rect = CGRectMake(0.0, 0.0, outputWidth.toDouble(), outputHeight.toDouble())
            CGContextDrawImage(context, rect, cgImage)

            // Apply LUT using trilinear interpolation matching Android exactly
            for (y in 0 until outputHeight) {
                for (x in 0 until outputWidth) {
                    val pixelIndex = (y * outputWidth + x) * 4

                    // Get original pixel values (0-255)
                    val r = rawData[pixelIndex].toInt()
                    val g = rawData[pixelIndex + 1].toInt()
                    val b = rawData[pixelIndex + 2].toInt()
                    val a = rawData[pixelIndex + 3]

                    // Convert to normalized values (0.0-1.0)
                    val rNorm = r / 255.0f
                    val gNorm = g / 255.0f
                    val bNorm = b / 255.0f

                    // Apply LUT with trilinear interpolation
                    val (newR, newG, newB) = applyLUTTrilinear(lut, rNorm, gNorm, bNorm)

                    // Convert back to 0-255 and clamp
                    rawData[pixelIndex] = (newR * 255.0f).toInt().coerceIn(0, 255).toUByte()
                    rawData[pixelIndex + 1] = (newG * 255.0f).toInt().coerceIn(0, 255).toUByte()
                    rawData[pixelIndex + 2] = (newB * 255.0f).toInt().coerceIn(0, 255).toUByte()
                    rawData[pixelIndex + 3] = a // Preserve alpha
                }
            }

            val outputCGImage = CGBitmapContextCreateImage(context) ?: return null
            val outputUIImage = UIImage.imageWithCGImage(outputCGImage)

            CGColorSpaceRelease(colorSpace)

            outputUIImage
        }
    }

    /**
     * Apply LUT using trilinear interpolation matching Android's exact algorithm
     */
    private fun applyLUTTrilinear(
        lut: LUT3D,
        r: Float,
        g: Float,
        b: Float
    ): Triple<Float, Float, Float> {
        val size = lut.size
        val scale = (size - 1).toFloat()

        // Clamp input values to [0, 1]
        val rClamped = r.coerceIn(0.0f, 1.0f)
        val gClamped = g.coerceIn(0.0f, 1.0f)
        val bClamped = b.coerceIn(0.0f, 1.0f)

        // Scale to LUT coordinates
        val rf = rClamped * scale
        val gf = gClamped * scale
        val bf = bClamped * scale

        // Get integer indices
        val r0 = floor(rf).toInt()
        val g0 = floor(gf).toInt()
        val b0 = floor(bf).toInt()

        val r1 = min(r0 + 1, size - 1)
        val g1 = min(g0 + 1, size - 1)
        val b1 = min(b0 + 1, size - 1)

        // Get fractional parts for interpolation
        val rd = rf - r0
        val gd = gf - g0
        val bd = bf - b0

        // Helper function to get color at specific indices
        // Using the same indexing as Android: r + g * size + b * size * size
        fun getColor(ri: Int, gi: Int, bi: Int): Triple<Float, Float, Float> {
            val index = (ri + gi * size + bi * size * size) * 3
            return if (index + 2 < lut.data.size) {
                Triple(lut.data[index], lut.data[index + 1], lut.data[index + 2])
            } else {
                Triple(0f, 0f, 0f)
            }
        }

        // Get the 8 corner points of the cube
        val c000 = getColor(r0, g0, b0)
        val c001 = getColor(r0, g0, b1)
        val c010 = getColor(r0, g1, b0)
        val c011 = getColor(r0, g1, b1)
        val c100 = getColor(r1, g0, b0)
        val c101 = getColor(r1, g0, b1)
        val c110 = getColor(r1, g1, b0)
        val c111 = getColor(r1, g1, b1)

        // Trilinear interpolation - exactly matching Android's order
        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        // Interpolate along R axis
        val c00r = lerp(c000.first, c100.first, rd)
        val c00g = lerp(c000.second, c100.second, rd)
        val c00b = lerp(c000.third, c100.third, rd)

        val c01r = lerp(c001.first, c101.first, rd)
        val c01g = lerp(c001.second, c101.second, rd)
        val c01b = lerp(c001.third, c101.third, rd)

        val c10r = lerp(c010.first, c110.first, rd)
        val c10g = lerp(c010.second, c110.second, rd)
        val c10b = lerp(c010.third, c110.third, rd)

        val c11r = lerp(c011.first, c111.first, rd)
        val c11g = lerp(c011.second, c111.second, rd)
        val c11b = lerp(c011.third, c111.third, rd)

        // Interpolate along G axis
        val c0r = lerp(c00r, c10r, gd)
        val c0g = lerp(c00g, c10g, gd)
        val c0b = lerp(c00b, c10b, gd)

        val c1r = lerp(c01r, c11r, gd)
        val c1g = lerp(c01g, c11g, gd)
        val c1b = lerp(c01b, c11b, gd)

        // Interpolate along B axis
        val resultR = lerp(c0r, c1r, bd)
        val resultG = lerp(c0g, c1g, bd)
        val resultB = lerp(c0b, c1b, bd)

        return Triple(resultR, resultG, resultB)
    }

    /**
     * Apply grain effect using GPU-accelerated Core Image filters
     */
    private fun processGrainWithGPU(
        inputImage: UIImage,
        intensity: Float
    ): UIImage? {
        // Convert UIImage to CIImage
        val ciImage = CIImage.imageWithCGImage(inputImage.CGImage ?: return null)

        // Create random noise generator filter
        val noiseFilter = CIFilter.filterWithName("CIRandomGenerator") ?: return null

        // Get noise output
        val noiseImage = noiseFilter.outputImage ?: return null

        // Crop noise to match input image extent
        val croppedNoise = noiseImage.imageByCroppingToRect(ciImage.extent)

        // Create color matrix to adjust noise intensity
        val colorMatrixFilter = CIFilter.filterWithName("CIColorMatrix") ?: return null
        colorMatrixFilter.setValue(croppedNoise, forKey = "inputImage")

        // Scale the noise intensity (similar to Android implementation)
        val scaleFactor = intensity * 0.4f

        // Set up the color matrix to scale the noise
        // This creates monochrome noise scaled by intensity
        colorMatrixFilter.setValue(CIVector(x = scaleFactor.toDouble(), Y = 0.0, Z = 0.0, W = 0.0), forKey = "inputRVector")
        colorMatrixFilter.setValue(CIVector(x = 0.0, Y = scaleFactor.toDouble(), Z = 0.0, W = 0.0), forKey = "inputGVector")
        colorMatrixFilter.setValue(CIVector(x = 0.0, Y = 0.0, Z = scaleFactor.toDouble(), W = 0.0), forKey = "inputBVector")
        colorMatrixFilter.setValue(CIVector(x = 0.0, Y = 0.0, Z = 0.0, W = 1.0), forKey = "inputAVector")
        colorMatrixFilter.setValue(CIVector(x = 0.0, Y = 0.0, Z = 0.0, W = 0.0), forKey = "inputBiasVector")

        val scaledNoise = colorMatrixFilter.outputImage ?: return null

        // Blend the noise with the original image using CISourceOverCompositing
        val blendFilter = CIFilter.filterWithName("CIColorDodgeBlending") ?: return null
        blendFilter.setValue(scaledNoise, forKey = "inputImage")
        blendFilter.setValue(ciImage, forKey = "inputBackgroundImage")

        val outputCIImage = blendFilter.outputImage ?: return null

        // Render to CGImage using GPU context
        val extent = outputCIImage.extent
        val cgImage = ciContext.createCGImage(outputCIImage, fromRect = extent) ?: return null

        // Convert back to UIImage
        return UIImage.imageWithCGImage(cgImage)
    }

    /**
     * Parse .cube LUT file - maintains compatibility with Android implementation
     */
    private fun parseCubeLUT(lutPath: String): LUT3D? {
        val file = fopen(lutPath, "r") ?: run {
            println("Failed to open LUT file: $lutPath")
            return null
        }

        var lutSize = 0
        val lutData = mutableListOf<Float>()

        memScoped {
            val buffer = allocArray<ByteVar>(512)

            while (fgets(buffer, 512, file) != null) {
                val line = buffer.toKString().trim()

                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue
                }

                // Parse LUT size
                if (line.startsWith("LUT_3D_SIZE")) {
                    val parts = line.split(" ", "\t").filter { it.isNotEmpty() }
                    if (parts.size >= 2) {
                        lutSize = parts[1].toIntOrNull() ?: 0
                        if (lutSize <= 0 || lutSize > 256) {
                            println("Invalid LUT size: $lutSize")
                            fclose(file)
                            return null
                        }
                        lutData.clear()
                    }
                }
                // Skip other metadata
                else if (line.startsWith("TITLE") ||
                         line.startsWith("DOMAIN_MIN") ||
                         line.startsWith("DOMAIN_MAX") ||
                         line.startsWith("LUT_1D_SIZE") ||
                         line.startsWith("LUT_1D_INPUT_RANGE")) {
                    continue
                }
                // Parse LUT data values
                else if (lutSize > 0) {
                    val parts = line.split(" ", "\t").filter { it.isNotEmpty() }
                    val values = parts.mapNotNull { it.trim().toFloatOrNull() }

                    if (values.size >= 3) {
                        // Store RGB values
                        lutData.add(values[0])
                        lutData.add(values[1])
                        lutData.add(values[2])
                    }
                }
            }
        }

        fclose(file)

        // Verify data size matches expectations
        val expectedSize = lutSize * lutSize * lutSize * 3
        if (lutData.size != expectedSize) {
            println("LUT data size mismatch: expected $expectedSize, got ${lutData.size}")
            // Allow small differences (like Android implementation)
            if (lutData.size > expectedSize && lutData.size - expectedSize < 30) {
                return LUT3D(lutSize, lutData.take(expectedSize).toFloatArray())
            }
            return null
        }

        println("Successfully loaded LUT with size: $lutSize")
        return LUT3D(lutSize, lutData.toFloatArray())
    }
}