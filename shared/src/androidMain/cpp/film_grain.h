#ifndef FILM_GRAIN_H
#define FILM_GRAIN_H

#include <cstdint>

class FilmGrain {
public:
    // Add film grain effect to image
    static void addGrain(
        uint8_t* pixels,
        int width,
        int height,
        float intensity
    );

private:
    // Generate pseudo-random noise
    static float randomNoise(int x, int y, int seed);

    // Perlin noise for more natural grain
    static float perlinNoise(float x, float y, int octaves, float persistence);
};

#endif // FILM_GRAIN_H