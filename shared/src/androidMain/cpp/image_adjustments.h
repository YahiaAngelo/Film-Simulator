#ifndef IMAGE_ADJUSTMENTS_H
#define IMAGE_ADJUSTMENTS_H

#include <cstdint>
#include <cmath>

/**
 * Structure to hold all image adjustment parameters
 */
struct ImageAdjustments {
    float contrast;              // Range: -1.0 to 1.0
    float brightness;            // Range: -1.0 to 1.0
    float saturation;            // Range: -1.0 to 1.0
    float temperature;           // Range: -1.0 to 1.0 (blue to orange)
    float exposure;              // Range: -2.0 to 2.0
    float grain;                 // Range: 0.0 to 1.0
    float chromaticAberration;   // Range: 0.0 to 1.0

    ImageAdjustments()
        : contrast(0.0f), brightness(0.0f), saturation(0.0f),
          temperature(0.0f), exposure(0.0f), grain(0.0f),
          chromaticAberration(0.0f) {}
};

/**
 * Helper class for applying image adjustments to pixel data
 */
class ImageAdjustmentProcessor {
public:
    /**
     * Apply all adjustments to a single pixel
     *
     * @param r Red channel (0-255)
     * @param g Green channel (0-255)
     * @param b Blue channel (0-255)
     * @param adjustments The adjustments to apply
     */
    static void applyAdjustmentsToPixel(
        uint8_t& r,
        uint8_t& g,
        uint8_t& b,
        const ImageAdjustments& adjustments,
        int x,
        int y,
        int width,
        int height,
        uint32_t seed = 0
    );

    /**
     * Apply adjustments to an entire image buffer
     *
     * @param pixels RGBA pixel data (4 bytes per pixel)
     * @param width Image width
     * @param height Image height
     * @param adjustments The adjustments to apply
     */
    static void applyAdjustmentsToImage(
        uint8_t* pixels,
        int width,
        int height,
        const ImageAdjustments& adjustments
    );

private:
    // Helper functions for individual adjustments
    static void applyExposure(float& r, float& g, float& b, float exposure);
    static void applyContrast(float& r, float& g, float& b, float contrast);
    static void applyBrightness(float& r, float& g, float& b, float brightness);
    static void applySaturation(float& r, float& g, float& b, float saturation);
    static void applyTemperature(float& r, float& g, float& b, float temperature);
    static void applyGrain(uint8_t& r, uint8_t& g, uint8_t& b, float grain, int x, int y, uint32_t seed);
    static void applyChromaticAberration(
        uint8_t* pixels,
        int width,
        int height,
        int x,
        int y,
        uint8_t& r,
        uint8_t& g,
        uint8_t& b,
        float strength
    );

    // Utility functions
    static float clamp(float value, float min, float max);
    static uint8_t floatToUint8(float value);
    static float uint8ToFloat(uint8_t value);

    // Simple pseudo-random number generator for grain
    static float noise(int x, int y, uint32_t seed);
};

#endif // IMAGE_ADJUSTMENTS_H
