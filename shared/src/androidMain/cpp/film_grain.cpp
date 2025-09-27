#include "film_grain.h"
#include <cmath>
#include <random>
#include <algorithm>

void FilmGrain::addGrain(
    uint8_t* pixels,
    int width,
    int height,
    float intensity) {

    // Clamp intensity to reasonable range
    intensity = std::max(0.0f, std::min(1.0f, intensity));

    std::random_device rd;
    std::mt19937 gen(rd());
    std::normal_distribution<float> dist(0.0f, intensity * 20.0f);

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int index = (y * width + x) * 4;

            // Generate grain value
            float grain = dist(gen);

            // Apply grain to RGB channels (not alpha)
            for (int c = 0; c < 3; ++c) {
                float value = pixels[index + c] + grain;
                pixels[index + c] = static_cast<uint8_t>(
                    std::max(0.0f, std::min(255.0f, value))
                );
            }
        }
    }
}

float FilmGrain::randomNoise(int x, int y, int seed) {
    int n = x + y * 57 + seed * 131;
    n = (n << 13) ^ n;
    float noise = (1.0f - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0f);
    return noise;
}

float FilmGrain::perlinNoise(float x, float y, int octaves, float persistence) {
    float total = 0;
    float frequency = 1;
    float amplitude = 1;
    float maxValue = 0;

    for (int i = 0; i < octaves; ++i) {
        total += randomNoise(
            static_cast<int>(x * frequency),
            static_cast<int>(y * frequency),
            i
        ) * amplitude;

        maxValue += amplitude;
        amplitude *= persistence;
        frequency *= 2;
    }

    return total / maxValue;
}