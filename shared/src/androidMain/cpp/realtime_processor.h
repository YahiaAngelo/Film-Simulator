#ifndef REALTIME_PROCESSOR_H
#define REALTIME_PROCESSOR_H

#include <jni.h>
#include <android/bitmap.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <EGL/egl.h>
#include <string>
#include <memory>
#include <vector>
#include <mutex>
#include <unordered_map>
#include <cstdint>
#include "lut_processor.h"

/**
 * Realtime image processor that keeps images in native memory
 * for zero-copy, real-time adjustments without file I/O
 */
class RealtimeProcessor {
public:
    RealtimeProcessor();
    ~RealtimeProcessor();

    // Load image once into native memory - returns handle
    int64_t loadImage(JNIEnv* env, jobject bitmap);

    // Load image from file path - returns handle
    int64_t loadImageFromPath(JNIEnv* env, const std::string& path);

    // Apply adjustments without file I/O - uses existing memory
    bool updateAdjustments(int64_t handle,
                         float exposure,
                         float contrast,
                         float brightness,
                         float saturation,
                         float temperature,
                         float grain,
                         float chromaticAberration);

    // Load and cache LUT in memory
    bool loadLUT(const std::string& lutPath);

    // Apply cached LUT to image
    bool applyLUT(int64_t handle);

    // Render directly to Android Surface
    bool renderToSurface(int64_t handle, ANativeWindow* window);

    // Export processed image to file (only when saving)
    bool exportImage(int64_t handle, const std::string& outputPath, int quality);

    // Release image from memory
    void releaseImage(int64_t handle);

    // Get image dimensions
    bool getImageDimensions(int64_t handle, int& width, int& height);

    // Direct bitmap access for preview (zero-copy)
    jobject getProcessedBitmap(JNIEnv* env, int64_t handle);

private:
    struct ImageData {
        uint8_t* originalData;      // Original image pixels (RGBA)
        uint8_t* processedData;     // Processed image pixels (RGBA)
        int width;
        int height;
        int stride;
        GLuint textureId;           // OpenGL texture for rendering
        bool isDirty;               // Needs reprocessing

        // Current adjustment values (cached to avoid reprocessing)
        float lastExposure = 0;
        float lastContrast = 0;
        float lastBrightness = 0;
        float lastSaturation = 0;
        float lastTemperature = 0;
        float lastGrain = 0;
        float lastChromaticAberration = 0;

        ImageData() : originalData(nullptr), processedData(nullptr),
                     width(0), height(0), stride(0), textureId(0), isDirty(true) {}
        ~ImageData();
    };

    // Image storage - using handles for safe access
    std::unordered_map<int64_t, std::unique_ptr<ImageData>> images;
    int64_t nextHandle = 1;
    std::mutex imageMutex;

    // Cached LUT data
    std::unique_ptr<LUT3D> lut3d;
    bool lutLoaded = false;

    // OpenGL context for rendering
    EGLDisplay eglDisplay;
    EGLContext eglContext;
    EGLSurface eglSurface;
    bool glInitialized = false;

    // Shader programs for rendering
    GLuint shaderProgram;
    GLuint vertexShader;
    GLuint fragmentShader;

    // Internal methods
    bool initializeGL();
    void cleanupGL();
    bool createTexture(ImageData* data);
    bool updateTexture(ImageData* data);
    void processImageInternal(ImageData* data);
    bool compileShader(GLuint& shader, GLenum type, const char* source);
    bool linkProgram();

    // Apply individual adjustments (in-place, no allocation)
    void applyExposure(uint8_t* data, int size, float exposure);
    void applyContrast(uint8_t* data, int size, float contrast);
    void applyBrightness(uint8_t* data, int size, float brightness);
    void applySaturation(uint8_t* data, int size, float saturation);
    void applyTemperature(uint8_t* data, int size, float temperature);
    void applyGrain(uint8_t* data, int size, float grain, int width, int height);
    void applyChromaticAberration(uint8_t* data, int width, int height, float strength);
};

#endif // REALTIME_PROCESSOR_H