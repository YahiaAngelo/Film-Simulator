#include "image_adjustments.h"
#include <algorithm>
#include <cmath>

// Clamp a value between min and max
float ImageAdjustmentProcessor::clamp(float value, float min, float max) {
    return std::max(min, std::min(max, value));
}

// Convert float [0-1] to uint8 [0-255]
uint8_t ImageAdjustmentProcessor::floatToUint8(float value) {
    return static_cast<uint8_t>(clamp(value * 255.0f, 0.0f, 255.0f));
}

// Convert uint8 [0-255] to float [0-1]
float ImageAdjustmentProcessor::uint8ToFloat(uint8_t value) {
    return static_cast<float>(value) / 255.0f;
}

// Proper pseudo-random noise generator for film grain
// Based on: fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453)
float ImageAdjustmentProcessor::noise(int x, int y, uint32_t seed) {
    // Normalize coordinates to [0, 1] range with seed influence
    float fx = static_cast<float>(x) + static_cast<float>(seed % 1000) * 0.001f;
    float fy = static_cast<float>(y) + static_cast<float>(seed / 1000) * 0.001f;

    // Dot product with magic numbers
    float dot = fx * 12.9898f + fy * 78.233f;

    // Sin and scale
    float sinVal = std::sin(dot) * 43758.5453f;

    // Fract (get fractional part)
    float fract = sinVal - std::floor(sinVal);

    // Return in range [0, 1]
    return fract;
}

// Apply exposure adjustment (affects overall brightness exponentially)
void ImageAdjustmentProcessor::applyExposure(float& r, float& g, float& b, float exposure) {
    if (exposure == 0.0f) return;

    // Convert exposure range (-2 to 2) to multiplier
    float multiplier = std::pow(2.0f, exposure);

    r *= multiplier;
    g *= multiplier;
    b *= multiplier;
}

// Apply contrast adjustment
void ImageAdjustmentProcessor::applyContrast(float& r, float& g, float& b, float contrast) {
    if (contrast == 0.0f) return;

    // Convert contrast from [-1, 1] to a multiplier [0.5, 1.5]
    float multiplier = 1.0f + contrast;

    // Apply contrast around midpoint (0.5)
    r = (r - 0.5f) * multiplier + 0.5f;
    g = (g - 0.5f) * multiplier + 0.5f;
    b = (b - 0.5f) * multiplier + 0.5f;
}

// Apply brightness adjustment
void ImageAdjustmentProcessor::applyBrightness(float& r, float& g, float& b, float brightness) {
    if (brightness == 0.0f) return;

    r += brightness;
    g += brightness;
    b += brightness;
}

// Apply saturation adjustment
void ImageAdjustmentProcessor::applySaturation(float& r, float& g, float& b, float saturation) {
    if (saturation == 0.0f) return;

    // Calculate luminance using standard weights
    float luminance = 0.299f * r + 0.587f * g + 0.114f * b;

    // Convert saturation from [-1, 1] to a multiplier [0, 2]
    float multiplier = 1.0f + saturation;

    // Interpolate between grayscale and full color
    r = luminance + (r - luminance) * multiplier;
    g = luminance + (g - luminance) * multiplier;
    b = luminance + (b - luminance) * multiplier;
}

// Apply temperature adjustment (blue to orange)
void ImageAdjustmentProcessor::applyTemperature(float& r, float& g, float& b, float temperature) {
    if (temperature == 0.0f) return;

    // Warm (positive): increase red/yellow, decrease blue
    // Cool (negative): decrease red/yellow, increase blue
    if (temperature > 0.0f) {
        // Warm
        r += temperature * 0.3f;
        g += temperature * 0.1f;
        b -= temperature * 0.2f;
    } else {
        // Cool
        r += temperature * 0.2f;
        g += temperature * 0.1f;
        b -= temperature * 0.3f;
    }
}

// Apply film grain effect
void ImageAdjustmentProcessor::applyGrain(uint8_t& r, uint8_t& g, uint8_t& b,
                                          float grain, int x, int y, uint32_t seed) {
    if (grain == 0.0f) return;

    // Generate noise value in [0, 1] range
    float noiseValue = noise(x, y, seed);

    // Convert to [-0.5, 0.5] range and scale by grain amount
    float diff = (noiseValue - 0.5f) * grain;

    // Convert RGB to float [0, 1]
    float rf = uint8ToFloat(r);
    float gf = uint8ToFloat(g);
    float bf = uint8ToFloat(b);

    // Add grain
    rf += diff;
    gf += diff;
    bf += diff;

    // Clamp and convert back to uint8
    r = floatToUint8(clamp(rf, 0.0f, 1.0f));
    g = floatToUint8(clamp(gf, 0.0f, 1.0f));
    b = floatToUint8(clamp(bf, 0.0f, 1.0f));
}

// Apply chromatic aberration effect
// Based on distance from center with directional offsets
void ImageAdjustmentProcessor::applyChromaticAberration(
    uint8_t* pixels,
    int width,
    int height,
    int x,
    int y,
    uint8_t& r,
    uint8_t& g,
    uint8_t& b,
    float strength
) {
    if (strength == 0.0f) return;

    // Calculate normalized UV coordinates [0, 1]
    float u = (static_cast<float>(x) + 0.5f) / static_cast<float>(width);
    float v = (static_cast<float>(y) + 0.5f) / static_cast<float>(height);

    // Calculate distance from center
    float dx = u - 0.5f;
    float dy = v - 0.5f;
    float d = std::sqrt(dx * dx + dy * dy);

    // Scale strength - use much larger multiplier for visible effect
    // strength is in [0, 1] range, multiply by 100 for visible aberration
    float offset = d * strength * 100.0f;

    // Red channel - offset outward from center
    float rU = u + dx * offset / static_cast<float>(width);
    float rV = v + dy * offset / static_cast<float>(height);
    int rX = static_cast<int>(clamp(rU * width, 0.0f, static_cast<float>(width - 1)));
    int rY = static_cast<int>(clamp(rV * height, 0.0f, static_cast<float>(height - 1)));
    int rIndex = (rY * width + rX) * 4;
    r = pixels[rIndex];

    // Green channel stays unchanged (no offset)

    // Blue channel - offset inward toward center
    float bU = u - dx * offset / static_cast<float>(width);
    float bV = v - dy * offset / static_cast<float>(height);
    int bX = static_cast<int>(clamp(bU * width, 0.0f, static_cast<float>(width - 1)));
    int bY = static_cast<int>(clamp(bV * height, 0.0f, static_cast<float>(height - 1)));
    int bIndex = (bY * width + bX) * 4;
    b = pixels[bIndex + 2];
}

// Apply all adjustments to a single pixel
void ImageAdjustmentProcessor::applyAdjustmentsToPixel(
    uint8_t& r,
    uint8_t& g,
    uint8_t& b,
    const ImageAdjustments& adjustments,
    int x,
    int y,
    int width,
    int height,
    uint32_t seed
) {
    // Convert to float for processing
    float rf = uint8ToFloat(r);
    float gf = uint8ToFloat(g);
    float bf = uint8ToFloat(b);

    // Apply adjustments in order (exposure first, then color adjustments)
    applyExposure(rf, gf, bf, adjustments.exposure);
    applyContrast(rf, gf, bf, adjustments.contrast);
    applyBrightness(rf, gf, bf, adjustments.brightness);
    applySaturation(rf, gf, bf, adjustments.saturation);
    applyTemperature(rf, gf, bf, adjustments.temperature);

    // Clamp after color adjustments
    rf = clamp(rf, 0.0f, 1.0f);
    gf = clamp(gf, 0.0f, 1.0f);
    bf = clamp(bf, 0.0f, 1.0f);

    // Convert back to uint8
    r = floatToUint8(rf);
    g = floatToUint8(gf);
    b = floatToUint8(bf);

    // Apply grain (works on uint8)
    applyGrain(r, g, b, adjustments.grain, x, y, seed);
}

// Apply adjustments to entire image buffer
void ImageAdjustmentProcessor::applyAdjustmentsToImage(
    uint8_t* pixels,
    int width,
    int height,
    const ImageAdjustments& adjustments
) {
    // Use a seed based on current time or a fixed value for consistent grain
    uint32_t seed = 12345;

    // First pass: Apply all adjustments except chromatic aberration
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int index = (y * width + x) * 4;
            uint8_t r = pixels[index];
            uint8_t g = pixels[index + 1];
            uint8_t b = pixels[index + 2];

            applyAdjustmentsToPixel(r, g, b, adjustments, x, y, width, height, seed);

            pixels[index] = r;
            pixels[index + 1] = g;
            pixels[index + 2] = b;
            // Alpha channel (index + 3) remains unchanged
        }
    }

    // Second pass: Apply chromatic aberration if needed
    // (Requires sampling from the adjusted image)
    if (adjustments.chromaticAberration > 0.0f) {
        // Create a copy of the buffer for sampling
        uint8_t* tempBuffer = new uint8_t[width * height * 4];
        std::memcpy(tempBuffer, pixels, width * height * 4);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = (y * width + x) * 4;
                uint8_t r = pixels[index];
                uint8_t g = pixels[index + 1];
                uint8_t b = pixels[index + 2];

                applyChromaticAberration(
                    tempBuffer, width, height, x, y,
                    r, g, b, adjustments.chromaticAberration
                );

                pixels[index] = r;
                pixels[index + 1] = g;
                pixels[index + 2] = b;
            }
        }

        delete[] tempBuffer;
    }
}
