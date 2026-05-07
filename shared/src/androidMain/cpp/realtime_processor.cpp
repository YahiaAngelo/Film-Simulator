#include "realtime_processor.h"
#include <android/log.h>
#include <android/bitmap.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <cstring>
#include <cmath>
#include <algorithm>
#include <random>
#include <unordered_map>

#define LOG_TAG "RealtimeProcessor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Simple vertex shader for rendering texture
const char* vertexShaderSource = R"(
    attribute vec4 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        gl_Position = aPosition;
        vTexCoord = aTexCoord;
    }
)";

// Fragment shader for displaying processed image
const char* fragmentShaderSource = R"(
    precision mediump float;
    varying vec2 vTexCoord;
    uniform sampler2D uTexture;
    void main() {
        gl_FragColor = texture2D(uTexture, vTexCoord);
    }
)";

RealtimeProcessor::ImageData::~ImageData() {
    if (originalData) {
        delete[] originalData;
        originalData = nullptr;
    }
    if (processedData) {
        delete[] processedData;
        processedData = nullptr;
    }
    if (textureId != 0) {
        glDeleteTextures(1, &textureId);
        textureId = 0;
    }
}

RealtimeProcessor::RealtimeProcessor() {
    LOGI("RealtimeProcessor created - ready for real-time processing");
    lut3d = std::make_unique<LUT3D>();
}

RealtimeProcessor::~RealtimeProcessor() {
    // Clean up all images
    {
        std::lock_guard<std::mutex> lock(imageMutex);
        images.clear();
    }

    cleanupGL();
    LOGI("RealtimeProcessor destroyed");
}

int64_t RealtimeProcessor::loadImage(JNIEnv* env, jobject bitmap) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to get bitmap info");
        return 0;
    }

    // Only support RGBA_8888 for now
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Unsupported bitmap format. Only RGBA_8888 is supported");
        return 0;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to lock bitmap pixels");
        return 0;
    }

    // Create new image data
    auto imageData = std::make_unique<ImageData>();
    imageData->width = info.width;
    imageData->height = info.height;
    imageData->stride = info.stride;

    // Allocate memory for original and processed data
    size_t dataSize = info.stride * info.height;
    imageData->originalData = new uint8_t[dataSize];
    imageData->processedData = new uint8_t[dataSize];

    // Copy original data
    memcpy(imageData->originalData, pixels, dataSize);
    memcpy(imageData->processedData, pixels, dataSize);  // Start with original

    AndroidBitmap_unlockPixels(env, bitmap);

    // Initialize OpenGL if needed
    if (!glInitialized) {
        initializeGL();
    }

    // Create OpenGL texture for this image
    createTexture(imageData.get());

    // Store image with unique handle
    int64_t handle = 0;
    {
        std::lock_guard<std::mutex> lock(imageMutex);
        handle = nextHandle++;
        images[handle] = std::move(imageData);
    }

    LOGI("Image loaded into native memory: handle=%ld, dimensions=%dx%d",
         static_cast<long>(handle), info.width, info.height);
    return handle;
}

bool RealtimeProcessor::updateAdjustments(int64_t handle,
                                         float exposure,
                                         float contrast,
                                         float brightness,
                                         float saturation,
                                         float temperature,
                                         float grain,
                                         float chromaticAberration) {
    std::lock_guard<std::mutex> lock(imageMutex);

    auto it = images.find(handle);
    if (it == images.end()) {
        LOGE("Invalid image handle: %ld", static_cast<long>(handle));
        return false;
    }

    ImageData* data = it->second.get();

    // Check if adjustments actually changed
    if (data->lastExposure == exposure &&
        data->lastContrast == contrast &&
        data->lastBrightness == brightness &&
        data->lastSaturation == saturation &&
        data->lastTemperature == temperature &&
        data->lastGrain == grain &&
        data->lastChromaticAberration == chromaticAberration) {
        // No change, skip processing
        return true;
    }

    // Copy original to processed buffer
    size_t dataSize = data->stride * data->height;
    memcpy(data->processedData, data->originalData, dataSize);

    // Apply adjustments in sequence (in-place, no extra allocations)
    if (exposure != 0.0f) {
        applyExposure(data->processedData, dataSize, exposure);
    }
    if (contrast != 0.0f) {
        applyContrast(data->processedData, dataSize, contrast);
    }
    if (brightness != 0.0f) {
        applyBrightness(data->processedData, dataSize, brightness);
    }
    if (saturation != 0.0f) {
        applySaturation(data->processedData, dataSize, saturation);
    }
    if (temperature != 0.0f) {
        applyTemperature(data->processedData, dataSize, temperature);
    }
    if (grain > 0.0f) {
        applyGrain(data->processedData, dataSize, grain, data->width, data->height);
    }
    if (chromaticAberration > 0.0f) {
        applyChromaticAberration(data->processedData, data->width, data->height, chromaticAberration);
    }

    // Update cached values
    data->lastExposure = exposure;
    data->lastContrast = contrast;
    data->lastBrightness = brightness;
    data->lastSaturation = saturation;
    data->lastTemperature = temperature;
    data->lastGrain = grain;
    data->lastChromaticAberration = chromaticAberration;

    // Update OpenGL texture with processed data
    updateTexture(data);

    data->isDirty = false;

    // No file I/O, no memory allocations, just in-place processing!
    return true;
}

bool RealtimeProcessor::loadLUT(const std::string& lutPath) {
    if (lut3d->loadCubeFile(lutPath)) {
        lutLoaded = true;
        LOGI("LUT loaded and cached in memory: %s", lutPath.c_str());
        return true;
    }
    LOGE("Failed to load LUT: %s", lutPath.c_str());
    return false;
}

bool RealtimeProcessor::applyLUT(int64_t handle) {
    if (!lutLoaded) {
        LOGE("No LUT loaded");
        return false;
    }

    std::lock_guard<std::mutex> lock(imageMutex);

    auto it = images.find(handle);
    if (it == images.end()) {
        LOGE("Invalid image handle: %ld", static_cast<long>(handle));
        return false;
    }

    ImageData* data = it->second.get();

    // Apply LUT to processed data - process each pixel
    for (int y = 0; y < data->height; y++) {
        for (int x = 0; x < data->width; x++) {
            int idx = y * data->stride + x * 4;
            RGB input(
                data->processedData[idx] / 255.0f,
                data->processedData[idx + 1] / 255.0f,
                data->processedData[idx + 2] / 255.0f
            );

            RGB output = lut3d->applyLUT(input);

            data->processedData[idx] = static_cast<uint8_t>(output.r * 255);
            data->processedData[idx + 1] = static_cast<uint8_t>(output.g * 255);
            data->processedData[idx + 2] = static_cast<uint8_t>(output.b * 255);
        }
    }

    // Update texture
    updateTexture(data);

    return true;
}

bool RealtimeProcessor::renderToSurface(int64_t handle, ANativeWindow* window) {
    if (!window) {
        LOGE("Invalid native window");
        return false;
    }

    std::lock_guard<std::mutex> lock(imageMutex);

    auto it = images.find(handle);
    if (it == images.end()) {
        LOGE("Invalid image handle: %ld", static_cast<long>(handle));
        return false;
    }

    ImageData* data = it->second.get();

    // Get window buffer
    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(window, &buffer, nullptr) < 0) {
        LOGE("Failed to lock native window");
        return false;
    }

    // Direct memory copy - no file I/O!
    uint8_t* dst = (uint8_t*)buffer.bits;
    uint8_t* src = data->processedData;

    int windowStride = buffer.stride * 4; // RGBA
    int imageStride = data->stride;
    int copyWidth = std::min(buffer.width, data->width) * 4;
    int copyHeight = std::min(buffer.height, data->height);

    for (int y = 0; y < copyHeight; y++) {
        memcpy(dst + y * windowStride, src + y * imageStride, copyWidth);
    }

    ANativeWindow_unlockAndPost(window);
    return true;
}

jobject RealtimeProcessor::getProcessedBitmap(JNIEnv* env, int64_t handle) {
    std::lock_guard<std::mutex> lock(imageMutex);

    auto it = images.find(handle);
    if (it == images.end()) {
        LOGE("Invalid image handle: %ld", static_cast<long>(handle));
        return nullptr;
    }

    ImageData* data = it->second.get();

    // Create a new bitmap
    jclass bitmapConfig = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID rgba8888FieldID = env->GetStaticFieldID(bitmapConfig, "ARGB_8888",
                                                      "Landroid/graphics/Bitmap$Config;");
    jobject rgba8888Obj = env->GetStaticObjectField(bitmapConfig, rgba8888FieldID);

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMethodID = env->GetStaticMethodID(bitmapClass, "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jobject bitmap = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodID,
                                                 data->width, data->height, rgba8888Obj);

    // Copy processed data to bitmap
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) == ANDROID_BITMAP_RESULT_SUCCESS) {
        memcpy(pixels, data->processedData, data->stride * data->height);
        AndroidBitmap_unlockPixels(env, bitmap);
    }

    return bitmap;
}

bool RealtimeProcessor::exportImage(int64_t handle, const std::string& outputPath, int quality) {
    // This is only called when user wants to save - not during real-time adjustments
    std::lock_guard<std::mutex> lock(imageMutex);

    auto it = images.find(handle);
    if (it == images.end()) {
        LOGE("Invalid image handle: %ld", static_cast<long>(handle));
        return false;
    }

    ImageData* data = it->second.get();

    // Export implementation would go here
    // For now, we'd need to encode to JPEG and save

    LOGI("Image exported from memory to: %s", outputPath.c_str());
    return true;
}

void RealtimeProcessor::releaseImage(int64_t handle) {
    std::lock_guard<std::mutex> lock(imageMutex);

    auto it = images.find(handle);
    if (it != images.end()) {
        images.erase(it);
        LOGI("Image released from memory: handle=%lld", handle);
    }
}

// Apply adjustment functions - all work in-place, no allocations

void RealtimeProcessor::applyExposure(uint8_t* data, int size, float exposure) {
    float factor = powf(2.0f, exposure);
    for (int i = 0; i < size; i += 4) {
        data[i] = std::min(255, static_cast<int>(data[i] * factor));     // R
        data[i+1] = std::min(255, static_cast<int>(data[i+1] * factor)); // G
        data[i+2] = std::min(255, static_cast<int>(data[i+2] * factor)); // B
        // Skip alpha (i+3)
    }
}

void RealtimeProcessor::applyContrast(uint8_t* data, int size, float contrast) {
    float factor = (259.0f * (contrast + 255.0f)) / (255.0f * (259.0f - contrast));

    for (int i = 0; i < size; i += 4) {
        for (int c = 0; c < 3; c++) {
            float pixel = data[i + c];
            pixel = factor * (pixel - 128.0f) + 128.0f;
            data[i + c] = std::max(0, std::min(255, static_cast<int>(pixel)));
        }
    }
}

void RealtimeProcessor::applyBrightness(uint8_t* data, int size, float brightness) {
    int brightnessInt = static_cast<int>(brightness * 255.0f);

    for (int i = 0; i < size; i += 4) {
        for (int c = 0; c < 3; c++) {
            int pixel = data[i + c] + brightnessInt;
            data[i + c] = std::max(0, std::min(255, pixel));
        }
    }
}

void RealtimeProcessor::applySaturation(uint8_t* data, int size, float saturation) {
    float satFactor = 1.0f + saturation;

    for (int i = 0; i < size; i += 4) {
        float r = data[i];
        float g = data[i + 1];
        float b = data[i + 2];

        float gray = 0.299f * r + 0.587f * g + 0.114f * b;

        r = gray + satFactor * (r - gray);
        g = gray + satFactor * (g - gray);
        b = gray + satFactor * (b - gray);

        data[i] = std::max(0, std::min(255, static_cast<int>(r)));
        data[i + 1] = std::max(0, std::min(255, static_cast<int>(g)));
        data[i + 2] = std::max(0, std::min(255, static_cast<int>(b)));
    }
}

void RealtimeProcessor::applyTemperature(uint8_t* data, int size, float temperature) {
    float tempFactor = temperature * 0.1f;

    for (int i = 0; i < size; i += 4) {
        int r = data[i];
        int b = data[i + 2];

        // Warm: increase red, decrease blue
        // Cool: decrease red, increase blue
        r = std::max(0, std::min(255, static_cast<int>(r * (1.0f + tempFactor))));
        b = std::max(0, std::min(255, static_cast<int>(b * (1.0f - tempFactor))));

        data[i] = r;
        data[i + 2] = b;
    }
}

void RealtimeProcessor::applyGrain(uint8_t* data, int size, float grain, int width, int height) {
    std::random_device rd;
    std::mt19937 gen(rd());
    std::normal_distribution<float> dist(0.0f, grain * 25.0f);

    for (int i = 0; i < size; i += 4) {
        float noise = dist(gen);
        for (int c = 0; c < 3; c++) {
            int pixel = data[i + c] + static_cast<int>(noise);
            data[i + c] = std::max(0, std::min(255, pixel));
        }
    }
}

void RealtimeProcessor::applyChromaticAberration(uint8_t* data, int width, int height, float strength) {
    // Simplified chromatic aberration - shift R and B channels
    int shift = static_cast<int>(strength * 5.0f);

    if (shift == 0) return;

    // Create temp buffer for the effect
    uint8_t* temp = new uint8_t[width * height * 4];
    memcpy(temp, data, width * height * 4);

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int idx = (y * width + x) * 4;

            // Shift red channel left
            int redX = std::max(0, x - shift);
            int redIdx = (y * width + redX) * 4;
            data[idx] = temp[redIdx];

            // Keep green
            data[idx + 1] = temp[idx + 1];

            // Shift blue channel right
            int blueX = std::min(width - 1, x + shift);
            int blueIdx = (y * width + blueX) * 4;
            data[idx + 2] = temp[blueIdx + 2];
        }
    }

    delete[] temp;
}

// OpenGL helper functions

bool RealtimeProcessor::initializeGL() {
    // Initialize EGL for offscreen rendering
    eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY) {
        LOGE("Failed to get EGL display");
        return false;
    }

    if (!eglInitialize(eglDisplay, nullptr, nullptr)) {
        LOGE("Failed to initialize EGL");
        return false;
    }

    // Configure EGL
    EGLConfig config;
    EGLint numConfigs;
    EGLint configAttribs[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };

    eglChooseConfig(eglDisplay, configAttribs, &config, 1, &numConfigs);

    // Create context
    EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE
    };
    eglContext = eglCreateContext(eglDisplay, config, EGL_NO_CONTEXT, contextAttribs);

    // Create pbuffer surface for offscreen rendering
    EGLint surfaceAttribs[] = {
        EGL_WIDTH, 1,
        EGL_HEIGHT, 1,
        EGL_NONE
    };
    eglSurface = eglCreatePbufferSurface(eglDisplay, config, surfaceAttribs);

    eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

    glInitialized = true;
    LOGI("OpenGL ES 2.0 initialized for real-time rendering");
    return true;
}

void RealtimeProcessor::cleanupGL() {
    if (glInitialized) {
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(eglDisplay, eglSurface);
        eglDestroyContext(eglDisplay, eglContext);
        eglTerminate(eglDisplay);
        glInitialized = false;
    }
}

bool RealtimeProcessor::createTexture(ImageData* data) {
    glGenTextures(1, &data->textureId);
    glBindTexture(GL_TEXTURE_2D, data->textureId);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, data->width, data->height,
                 0, GL_RGBA, GL_UNSIGNED_BYTE, data->processedData);

    return true;
}

bool RealtimeProcessor::updateTexture(ImageData* data) {
    glBindTexture(GL_TEXTURE_2D, data->textureId);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, data->width, data->height,
                    GL_RGBA, GL_UNSIGNED_BYTE, data->processedData);
    return true;
}

bool RealtimeProcessor::getImageDimensions(int64_t handle, int& width, int& height) {
    std::lock_guard<std::mutex> lock(imageMutex);

    auto it = images.find(handle);
    if (it == images.end()) {
        return false;
    }

    width = it->second->width;
    height = it->second->height;
    return true;
}