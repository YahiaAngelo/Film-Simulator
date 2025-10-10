#include "lut_processor.h"
#include <fstream>
#include <sstream>
#include <algorithm>
#include <cmath>
#include <android/log.h>
#include <thread>
#include <vector>

#ifdef __ARM_NEON
#include <arm_neon.h>
#endif

#ifdef _OPENMP
#include <omp.h>
#endif

#define LOG_TAG "LUTProcessor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

LUT3D::LUT3D() : size(0), loaded(false) {}

LUT3D::~LUT3D() {}

bool LUT3D::loadCubeFile(const std::string& filePath) {
    std::ifstream file(filePath);
    if (!file.is_open()) {
        LOGE("Failed to open cube file: %s", filePath.c_str());
        return false;
    }

    std::string line;
    bool inLUTData = false;

    while (std::getline(file, line)) {
        // Skip comments and empty lines
        if (line.empty() || line[0] == '#') {
            continue;
        }

        // Parse LUT size
        if (line.find("LUT_3D_SIZE") != std::string::npos) {
            std::istringstream iss(line);
            std::string token;
            iss >> token >> size;

            if (size <= 0 || size > 256) {
                LOGE("Invalid LUT size: %d", size);
                return false;
            }

            data.reserve(size * size * size);
            inLUTData = true;
        }
        // Parse LUT data
        else if (inLUTData && !line.empty()) {
            parseLUTData(line);
        }
    }

    file.close();

    if (data.size() != size * size * size) {
        LOGE("LUT data size mismatch. Expected: %d, Got: %zu",
             size * size * size, data.size());
        return false;
    }

    loaded = true;
    LOGI("Successfully loaded LUT with size: %d", size);
    return true;
}

void LUT3D::parseLUTData(const std::string& line) {
    std::istringstream iss(line);
    float r, g, b;

    if (iss >> r >> g >> b) {
        data.emplace_back(r, g, b);
    }
}

RGB LUT3D::applyLUT(const RGB& input) const {
    if (!loaded || data.empty()) {
        return input;
    }

    // Clamp input values to [0, 1]
    float r = std::max(0.0f, std::min(1.0f, input.r));
    float g = std::max(0.0f, std::min(1.0f, input.g));
    float b = std::max(0.0f, std::min(1.0f, input.b));

    return trilinearInterpolate(r, g, b);
}

RGB LUT3D::trilinearInterpolate(float r, float g, float b) const {
    float scale = size - 1.0f;

    float rf = r * scale;
    float gf = g * scale;
    float bf = b * scale;

    int r0 = static_cast<int>(std::floor(rf));
    int g0 = static_cast<int>(std::floor(gf));
    int b0 = static_cast<int>(std::floor(bf));

    int r1 = std::min(r0 + 1, size - 1);
    int g1 = std::min(g0 + 1, size - 1);
    int b1 = std::min(b0 + 1, size - 1);

    float rd = rf - r0;
    float gd = gf - g0;
    float bd = bf - b0;

    // Get the 8 corner points of the cube
    auto getIndex = [this](int r, int g, int b) {
        return r + g * size + b * size * size;
    };

    const RGB& c000 = data[getIndex(r0, g0, b0)];
    const RGB& c001 = data[getIndex(r0, g0, b1)];
    const RGB& c010 = data[getIndex(r0, g1, b0)];
    const RGB& c011 = data[getIndex(r0, g1, b1)];
    const RGB& c100 = data[getIndex(r1, g0, b0)];
    const RGB& c101 = data[getIndex(r1, g0, b1)];
    const RGB& c110 = data[getIndex(r1, g1, b0)];
    const RGB& c111 = data[getIndex(r1, g1, b1)];

    // Trilinear interpolation
    auto lerp = [](float a, float b, float t) { return a + (b - a) * t; };

    RGB c00, c01, c10, c11, c0, c1;

    c00.r = lerp(c000.r, c100.r, rd);
    c00.g = lerp(c000.g, c100.g, rd);
    c00.b = lerp(c000.b, c100.b, rd);

    c01.r = lerp(c001.r, c101.r, rd);
    c01.g = lerp(c001.g, c101.g, rd);
    c01.b = lerp(c001.b, c101.b, rd);

    c10.r = lerp(c010.r, c110.r, rd);
    c10.g = lerp(c010.g, c110.g, rd);
    c10.b = lerp(c010.b, c110.b, rd);

    c11.r = lerp(c011.r, c111.r, rd);
    c11.g = lerp(c011.g, c111.g, rd);
    c11.b = lerp(c011.b, c111.b, rd);

    c0.r = lerp(c00.r, c10.r, gd);
    c0.g = lerp(c00.g, c10.g, gd);
    c0.b = lerp(c00.b, c10.b, gd);

    c1.r = lerp(c01.r, c11.r, gd);
    c1.g = lerp(c01.g, c11.g, gd);
    c1.b = lerp(c01.b, c11.b, gd);

    RGB result;
    result.r = lerp(c0.r, c1.r, bd);
    result.g = lerp(c0.g, c1.g, bd);
    result.b = lerp(c0.b, c1.b, bd);

    return result;
}

bool processImageWithLUT(
    uint8_t* inputPixels,
    uint8_t* outputPixels,
    int width,
    int height,
    const std::string& lutPath,
    bool createThumbnail) {

    LUT3D lut;
    if (!lut.loadCubeFile(lutPath)) {
        LOGE("Failed to load LUT file: %s", lutPath.c_str());
        return false;
    }

    if (!inputPixels || !outputPixels) {
        LOGE("Null pixel buffers");
        return false;
    }

    const int totalPixels = width * height;

    // Use OpenMP parallelization if available
    #ifdef _OPENMP
    const int numThreads = std::min(omp_get_max_threads(), 8);
    omp_set_num_threads(numThreads);
    LOGI("Processing with %d threads", numThreads);

    #pragma omp parallel for schedule(dynamic, 1024)
    #endif
    for (int i = 0; i < totalPixels; ++i) {
        const int pixelIndex = i * 4; // RGBA format

        // Prefetch next cache line for better performance
        #ifdef __builtin_prefetch
        if (i < totalPixels - 16) {
            __builtin_prefetch(&inputPixels[pixelIndex + 64], 0, 1);
        }
        #endif

        // Use constant for division optimization
        constexpr float inv255 = 1.0f / 255.0f;

        // Convert to normalized RGB
        const float r = inputPixels[pixelIndex] * inv255;
        const float g = inputPixels[pixelIndex + 1] * inv255;
        const float b = inputPixels[pixelIndex + 2] * inv255;

        RGB inputRGB(r, g, b);

        // Apply LUT
        RGB outputRGB = lut.applyLUT(inputRGB);

        // Convert back using multiplication instead of division
        constexpr float scale255 = 255.0f;

        // Clamp values to valid range and write to output
        outputPixels[pixelIndex] = static_cast<uint8_t>(
            std::max(0.0f, std::min(255.0f, outputRGB.r * scale255))
        );
        outputPixels[pixelIndex + 1] = static_cast<uint8_t>(
            std::max(0.0f, std::min(255.0f, outputRGB.g * scale255))
        );
        outputPixels[pixelIndex + 2] = static_cast<uint8_t>(
            std::max(0.0f, std::min(255.0f, outputRGB.b * scale255))
        );
        outputPixels[pixelIndex + 3] = inputPixels[pixelIndex + 3]; // Preserve alpha
    }

    return true;
}