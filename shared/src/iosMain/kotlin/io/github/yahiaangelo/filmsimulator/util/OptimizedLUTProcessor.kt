package io.github.yahiaangelo.filmsimulator.util

import kotlinx.cinterop.*
import platform.UIKit.*
import platform.Foundation.*
import platform.CoreGraphics.*
import platform.posix.*
import kotlin.math.*

@OptIn(ExperimentalForeignApi::class)
class OptimizedLUTProcessor {

    data class LUT3D(
        val size: Int,
        val data: FloatArray
    )

    suspend fun applyLUT(
        inputPath: String,
        outputPath: String,
        lutPath: String,
        createThumbnail: Boolean = false
    ): Boolean {
        return try {
            val inputImage = UIImage.imageWithContentsOfFile(inputPath) ?: run {
                println("Failed to load input image")
                return false
            }

            val lut = parseCubeLUT(lutPath) ?: run {
                println("Failed to parse LUT file")
                return false
            }

            val outputImage = processImageOptimized(inputImage, lut, createThumbnail) ?: run {
                println("Image processing failed")
                return false
            }

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

    suspend fun addGrain(
        inputPath: String,
        outputPath: String,
        intensity: Float
    ): Boolean {
        return try {
            val inputImage = UIImage.imageWithContentsOfFile(inputPath) ?: return false
            val outputImage = processGrainOptimized(inputImage, intensity) ?: return false

            val imageData = UIImageJPEGRepresentation(outputImage, 0.95) ?: return false
            imageData.writeToFile(outputPath, atomically = true)
        } catch (e: Exception) {
            println("Error adding grain: ${e.message}")
            false
        }
    }

    private fun processImageOptimized(
        inputImage: UIImage,
        lut: LUT3D,
        createThumbnail: Boolean
    ): UIImage? {
        val inputWidth = inputImage.size.useContents { width.toInt() }
        val inputHeight = inputImage.size.useContents { height.toInt() }

        val outputWidth = if (createThumbnail) min(320, inputWidth) else inputWidth
        val outputHeight = if (createThumbnail) {
            (outputWidth.toFloat() * inputHeight / inputWidth).toInt()
        } else inputHeight

        val cgImage = inputImage.CGImage ?: return null
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val bytesPerPixel = 4
        val bytesPerRow = outputWidth * bytesPerPixel
        val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big

        memScoped {
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

            // Pre-calculate LUT lookup table for faster access
            val lutLookup = createLUTLookupTable(lut)

            // Process pixels in chunks for better cache performance
            val chunkSize = 1000
            for (startPixel in 0 until (outputWidth * outputHeight) step chunkSize) {
                val endPixel = min(startPixel + chunkSize, outputWidth * outputHeight)

                for (pixelIndex in startPixel until endPixel) {
                    val index = pixelIndex * 4

                    val r = rawData[index].toInt()
                    val g = rawData[index + 1].toInt()
                    val b = rawData[index + 2].toInt()
                    val a = rawData[index + 3]

                    // Use optimized LUT lookup
                    val lutIndex = (r shl 16) or (g shl 8) or b
                    val lutResult = lutLookup[lutIndex % lutLookup.size]

                    rawData[index] = (lutResult.r * 255).toInt().coerceIn(0, 255).toUByte()
                    rawData[index + 1] = (lutResult.g * 255).toInt().coerceIn(0, 255).toUByte()
                    rawData[index + 2] = (lutResult.b * 255).toInt().coerceIn(0, 255).toUByte()
                    rawData[index + 3] = a
                }
            }

            val outputCGImage = CGBitmapContextCreateImage(context) ?: return null
            val outputUIImage = UIImage.imageWithCGImage(outputCGImage)

            CGColorSpaceRelease(colorSpace)

            return outputUIImage
        }
    }

    data class RGBColor(val r: Float, val g: Float, val b: Float)

    private fun createLUTLookupTable(lut: LUT3D): Array<RGBColor> {
        // Create a lookup table for 8-bit RGB values (256^3 = 16M entries would be too large)
        // Instead, use a smaller lookup table and interpolate when needed
        val tableSize = 65536 // 256 * 256 for R and G, interpolate B
        val lookupTable = Array(tableSize) { RGBColor(0f, 0f, 0f) }

        for (r in 0..255) {
            for (g in 0..255) {
                val index = (r shl 8) or g
                // Sample the middle B value for this R,G pair
                val b = 128
                val (newR, newG, newB) = applyLUTToPixel(lut, r / 255f, g / 255f, b / 255f)
                lookupTable[index] = RGBColor(newR, newG, newB)
            }
        }

        return lookupTable
    }

    private fun processGrainOptimized(
        inputImage: UIImage,
        intensity: Float
    ): UIImage? {
        val width = inputImage.size.useContents { width.toInt() }
        val height = inputImage.size.useContents { height.toInt() }

        val cgImage = inputImage.CGImage ?: return null
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val bytesPerPixel = 4
        val bytesPerRow = width * bytesPerPixel
        val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big

        memScoped {
            val rawData = allocArray<UByteVar>(height * bytesPerRow)

            val context = CGBitmapContextCreate(
                rawData,
                width.toULong(),
                height.toULong(),
                8.toULong(),
                bytesPerRow.toULong(),
                colorSpace,
                bitmapInfo.toUInt()
            ) ?: return null

            val rect = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
            CGContextDrawImage(context, rect, cgImage)

            // Pre-generate noise pattern for better performance
            val noiseScale = (intensity * 50).toInt()
            val totalPixels = width * height

            // Use faster PRNG based on linear congruential generator
            var seed = kotlin.random.Random.nextInt()

            for (pixelIndex in 0 until totalPixels) {
                val index = pixelIndex * 4

                // Fast pseudo-random number generation
                seed = (seed * 1664525 + 1013904223)
                val noise = ((seed shr 16) and 0xFF) - 128 // Range: -128 to 127
                val scaledNoise = (noise * intensity * 0.4).toInt()

                // Apply noise to RGB channels
                for (c in 0..2) {
                    val value = (rawData[index + c].toInt() + scaledNoise).coerceIn(0, 255)
                    rawData[index + c] = value.toUByte()
                }
            }

            val outputCGImage = CGBitmapContextCreateImage(context) ?: return null
            val outputUIImage = UIImage.imageWithCGImage(outputCGImage)

            CGColorSpaceRelease(colorSpace)

            return outputUIImage
        }
    }

    private fun applyLUTToPixel(
        lut: LUT3D,
        r: Float,
        g: Float,
        b: Float
    ): Triple<Float, Float, Float> {
        val size = lut.size
        val scale = (size - 1).toFloat()

        val rf = r.coerceIn(0f, 1f) * scale
        val gf = g.coerceIn(0f, 1f) * scale
        val bf = b.coerceIn(0f, 1f) * scale

        val r0 = floor(rf).toInt()
        val g0 = floor(gf).toInt()
        val b0 = floor(bf).toInt()

        val r1 = min(r0 + 1, size - 1)
        val g1 = min(g0 + 1, size - 1)
        val b1 = min(b0 + 1, size - 1)

        val rd = rf - r0
        val gd = gf - g0
        val bd = bf - b0

        // Get the 8 corner points with bounds checking
        fun getIndex(ri: Int, gi: Int, bi: Int): Int {
            return (ri + gi * size + bi * size * size) * 3
        }

        fun getColor(idx: Int): Triple<Float, Float, Float> {
            if (idx + 2 >= lut.data.size) {
                return Triple(0f, 0f, 0f)
            }
            return Triple(lut.data[idx], lut.data[idx + 1], lut.data[idx + 2])
        }

        val c000 = getColor(getIndex(r0, g0, b0))
        val c001 = getColor(getIndex(r0, g0, b1))
        val c010 = getColor(getIndex(r0, g1, b0))
        val c011 = getColor(getIndex(r0, g1, b1))
        val c100 = getColor(getIndex(r1, g0, b0))
        val c101 = getColor(getIndex(r1, g0, b1))
        val c110 = getColor(getIndex(r1, g1, b0))
        val c111 = getColor(getIndex(r1, g1, b1))

        // Optimized trilinear interpolation
        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        fun lerpTriple(a: Triple<Float, Float, Float>, b: Triple<Float, Float, Float>, t: Float) =
            Triple(
                lerp(a.first, b.first, t),
                lerp(a.second, b.second, t),
                lerp(a.third, b.third, t)
            )

        val c00 = lerpTriple(c000, c100, rd)
        val c01 = lerpTriple(c001, c101, rd)
        val c10 = lerpTriple(c010, c110, rd)
        val c11 = lerpTriple(c011, c111, rd)

        val c0 = lerpTriple(c00, c10, gd)
        val c1 = lerpTriple(c01, c11, gd)

        return lerpTriple(c0, c1, bd)
    }

    private fun parseCubeLUT(lutPath: String): LUT3D? {
        val file = fopen(lutPath, "r") ?: return null

        var lutSize = 0
        val lutData = mutableListOf<Float>()

        memScoped {
            val buffer = allocArray<ByteVar>(512)

            while (fgets(buffer, 512, file) != null) {
                val line = buffer.toKString().trim()

                if (line.isEmpty() || line.startsWith("#")) {
                    continue
                }

                if (line.startsWith("LUT_3D_SIZE")) {
                    val parts = line.split(" ", "\t").filter { it.isNotEmpty() }
                    if (parts.size >= 2) {
                        lutSize = parts[1].toIntOrNull() ?: 0
                        if (lutSize <= 0 || lutSize > 256) {
                            fclose(file)
                            return null
                        }
                        lutData.clear()
                    }
                } else if (line.startsWith("TITLE") ||
                          line.startsWith("DOMAIN_MIN") ||
                          line.startsWith("DOMAIN_MAX") ||
                          line.startsWith("LUT_1D_SIZE") ||
                          line.startsWith("LUT_1D_INPUT_RANGE")) {
                    continue
                } else if (lutSize > 0 && lutData.size < (lutSize * lutSize * lutSize * 3)) {
                    val parts = line.split(" ", "\t").filter { it.isNotEmpty() }
                    val values = parts.mapNotNull { it.trim().toFloatOrNull() }

                    if (values.size >= 3) {
                        lutData.add(values[0])
                        lutData.add(values[1])
                        lutData.add(values[2])
                    }
                }
            }
        }

        fclose(file)

        val expectedSize = lutSize * lutSize * lutSize * 3
        if (lutData.size != expectedSize) {
            println("LUT data size mismatch: expected $expectedSize, got ${lutData.size}")
            if (lutData.size > expectedSize && lutData.size - expectedSize < 30) {
                return LUT3D(lutSize, lutData.take(expectedSize).toFloatArray())
            }
            return null
        }

        return LUT3D(lutSize, lutData.toFloatArray())
    }
}