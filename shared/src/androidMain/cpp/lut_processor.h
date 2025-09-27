#ifndef LUT_PROCESSOR_H
#define LUT_PROCESSOR_H

#include <vector>
#include <string>
#include <memory>

struct RGB {
    float r, g, b;

    RGB() : r(0), g(0), b(0) {}
    RGB(float r_, float g_, float b_) : r(r_), g(g_), b(b_) {}
};

class LUT3D {
public:
    LUT3D();
    ~LUT3D();

    // Load a .cube file
    bool loadCubeFile(const std::string& filePath);

    // Apply LUT to RGB values
    RGB applyLUT(const RGB& input) const;

    // Get LUT size
    int getSize() const { return size; }

    // Check if LUT is loaded
    bool isLoaded() const { return loaded; }

private:
    int size;
    std::vector<RGB> data;
    bool loaded;

    // Trilinear interpolation for smooth LUT application
    RGB trilinearInterpolate(float r, float g, float b) const;

    // Parse cube file
    bool parseCubeLine(const std::string& line);
    void parseLUTData(const std::string& line);
};

// Process image with LUT
bool processImageWithLUT(
    uint8_t* inputPixels,
    uint8_t* outputPixels,
    int width,
    int height,
    const std::string& lutPath,
    bool createThumbnail = false
);

#endif // LUT_PROCESSOR_H