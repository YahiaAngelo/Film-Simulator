#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "image_processor.h"
#include "image_adjustments.h"
#include "film_grain.h"
#include "lut_processor.h"
#include "realtime_processor.h"
#include <memory>

#define LOG_TAG "FilmSimulatorJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global realtime processor instance - persists across calls
static std::unique_ptr<RealtimeProcessor> g_realtimeProcessor;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_NativeLUTProcessor_applyLUTNative(
    JNIEnv* env,
    jobject /* this */,
    jobject inputBitmap,
    jobject outputBitmap,
    jstring lutPath) {

    const char* lutPathStr = env->GetStringUTFChars(lutPath, nullptr);
    if (!lutPathStr) {
        LOGE("Failed to get LUT path string");
        return JNI_FALSE;
    }

    bool result = ImageProcessor::processBitmap(
        env,
        inputBitmap,
        outputBitmap,
        std::string(lutPathStr)
    );

    env->ReleaseStringUTFChars(lutPath, lutPathStr);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_NativeLUTProcessor_applyLUTToFile(
    JNIEnv* env,
    jobject /* this */,
    jstring inputPath,
    jstring outputPath,
    jstring lutPath,
    jboolean createThumbnail) {

    const char* inputPathStr = env->GetStringUTFChars(inputPath, nullptr);
    const char* outputPathStr = env->GetStringUTFChars(outputPath, nullptr);
    const char* lutPathStr = env->GetStringUTFChars(lutPath, nullptr);

    if (!inputPathStr || !outputPathStr || !lutPathStr) {
        LOGE("Failed to get path strings");
        return JNI_FALSE;
    }

    bool result = ImageProcessor::processImageFile(
        env,
        std::string(inputPathStr),
        std::string(outputPathStr),
        std::string(lutPathStr),
        createThumbnail
    );

    env->ReleaseStringUTFChars(inputPath, inputPathStr);
    env->ReleaseStringUTFChars(outputPath, outputPathStr);
    env->ReleaseStringUTFChars(lutPath, lutPathStr);

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_NativeLUTProcessor_addFilmGrainNative(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap,
    jfloat intensity) {

    AndroidBitmapInfo info;
    void* pixels;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get bitmap info");
        return;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format must be RGBA_8888");
        return;
    }

    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock bitmap pixels");
        return;
    }

    FilmGrain::addGrain(
        static_cast<uint8_t*>(pixels),
        info.width,
        info.height,
        intensity
    );

    AndroidBitmap_unlockPixels(env, bitmap);
}

JNIEXPORT jboolean JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_NativeImageAdjustmentProcessor_applyAdjustmentsNative(
    JNIEnv* env,
    jobject /* this */,
    jobject inputBitmap,
    jobject outputBitmap,
    jfloat contrast,
    jfloat brightness,
    jfloat saturation,
    jfloat temperature,
    jfloat exposure,
    jfloat grain,
    jfloat chromaticAberration) {

    // Create adjustments structure
    ImageAdjustments adjustments;
    adjustments.contrast = contrast;
    adjustments.brightness = brightness;
    adjustments.saturation = saturation;
    adjustments.temperature = temperature;
    adjustments.exposure = exposure;
    adjustments.grain = grain;
    adjustments.chromaticAberration = chromaticAberration;

    LOGI("Applying adjustments: contrast=%.2f, brightness=%.2f, saturation=%.2f, "
         "temperature=%.2f, exposure=%.2f, grain=%.2f, aberration=%.2f",
         contrast, brightness, saturation, temperature, exposure, grain, chromaticAberration);

    bool result = ImageProcessor::processBitmapWithAdjustments(
        env,
        inputBitmap,
        outputBitmap,
        adjustments
    );

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_NativeImageAdjustmentProcessor_applyLutAndAdjustmentsNative(
    JNIEnv* env,
    jobject /* this */,
    jobject inputBitmap,
    jobject outputBitmap,
    jstring lutPath,
    jfloat contrast,
    jfloat brightness,
    jfloat saturation,
    jfloat temperature,
    jfloat exposure,
    jfloat grain,
    jfloat chromaticAberration) {

    const char* lutPathStr = env->GetStringUTFChars(lutPath, nullptr);
    if (!lutPathStr) {
        LOGE("Failed to get LUT path string");
        return JNI_FALSE;
    }

    // Create adjustments structure
    ImageAdjustments adjustments;
    adjustments.contrast = contrast;
    adjustments.brightness = brightness;
    adjustments.saturation = saturation;
    adjustments.temperature = temperature;
    adjustments.exposure = exposure;
    adjustments.grain = grain;
    adjustments.chromaticAberration = chromaticAberration;

    LOGI("Applying LUT and adjustments: LUT=%s", lutPathStr);

    bool result = ImageProcessor::processBitmapWithLutAndAdjustments(
        env,
        inputBitmap,
        outputBitmap,
        std::string(lutPathStr),
        adjustments
    );

    env->ReleaseStringUTFChars(lutPath, lutPathStr);
    return result ? JNI_TRUE : JNI_FALSE;
}

// ============================================================================
// REALTIME PROCESSOR JNI FUNCTIONS
// These keep images in native memory for zero-copy real-time adjustments
// ============================================================================

JNIEXPORT void JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_RealtimeImageProcessor_initializeNative(
    JNIEnv* env,
    jobject /* this */) {

    if (!g_realtimeProcessor) {
        g_realtimeProcessor = std::make_unique<RealtimeProcessor>();
        LOGI("RealtimeProcessor initialized for zero-copy processing");
    }
}

JNIEXPORT jlong JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_RealtimeImageProcessor_loadImageNative(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap) {

    if (!g_realtimeProcessor) {
        g_realtimeProcessor = std::make_unique<RealtimeProcessor>();
    }

    int64_t handle = g_realtimeProcessor->loadImage(env, bitmap);
    return static_cast<jlong>(handle);
}

JNIEXPORT jboolean JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_RealtimeImageProcessor_updateAdjustmentsNative(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jfloat exposure,
    jfloat contrast,
    jfloat brightness,
    jfloat saturation,
    jfloat temperature,
    jfloat grain,
    jfloat chromaticAberration) {

    if (!g_realtimeProcessor) {
        LOGE("RealtimeProcessor not initialized");
        return JNI_FALSE;
    }

    bool result = g_realtimeProcessor->updateAdjustments(
        static_cast<int64_t>(handle),
        exposure, contrast, brightness, saturation,
        temperature, grain, chromaticAberration
    );

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_RealtimeImageProcessor_loadLUTNative(
    JNIEnv* env,
    jobject /* this */,
    jstring lutPath) {

    if (!g_realtimeProcessor) {
        g_realtimeProcessor = std::make_unique<RealtimeProcessor>();
    }

    const char* lutPathStr = env->GetStringUTFChars(lutPath, nullptr);
    if (!lutPathStr) {
        LOGE("Failed to get LUT path string");
        return JNI_FALSE;
    }

    bool result = g_realtimeProcessor->loadLUT(std::string(lutPathStr));
    env->ReleaseStringUTFChars(lutPath, lutPathStr);

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_RealtimeImageProcessor_applyLUTNative(
    JNIEnv* env,
    jobject /* this */,
    jlong handle) {

    if (!g_realtimeProcessor) {
        LOGE("RealtimeProcessor not initialized");
        return JNI_FALSE;
    }

    bool result = g_realtimeProcessor->applyLUT(static_cast<int64_t>(handle));
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobject JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_RealtimeImageProcessor_getProcessedBitmapNative(
    JNIEnv* env,
    jobject /* this */,
    jlong handle) {

    if (!g_realtimeProcessor) {
        LOGE("RealtimeProcessor not initialized");
        return nullptr;
    }

    return g_realtimeProcessor->getProcessedBitmap(env, static_cast<int64_t>(handle));
}

JNIEXPORT jboolean JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_RealtimeImageProcessor_renderToSurfaceNative(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jobject surface) {

    if (!g_realtimeProcessor) {
        LOGE("RealtimeProcessor not initialized");
        return JNI_FALSE;
    }

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        LOGE("Failed to get native window from surface");
        return JNI_FALSE;
    }

    bool result = g_realtimeProcessor->renderToSurface(
        static_cast<int64_t>(handle), window
    );

    ANativeWindow_release(window);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_RealtimeImageProcessor_exportImageNative(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jstring outputPath,
    jint quality) {

    if (!g_realtimeProcessor) {
        LOGE("RealtimeProcessor not initialized");
        return JNI_FALSE;
    }

    const char* outputPathStr = env->GetStringUTFChars(outputPath, nullptr);
    if (!outputPathStr) {
        LOGE("Failed to get output path string");
        return JNI_FALSE;
    }

    bool result = g_realtimeProcessor->exportImage(
        static_cast<int64_t>(handle),
        std::string(outputPathStr),
        quality
    );

    env->ReleaseStringUTFChars(outputPath, outputPathStr);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_RealtimeImageProcessor_releaseImageNative(
    JNIEnv* env,
    jobject /* this */,
    jlong handle) {

    if (!g_realtimeProcessor) {
        return;
    }

    g_realtimeProcessor->releaseImage(static_cast<int64_t>(handle));
}

JNIEXPORT jintArray JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_RealtimeImageProcessor_getImageDimensionsNative(
    JNIEnv* env,
    jobject /* this */,
    jlong handle) {

    if (!g_realtimeProcessor) {
        LOGE("RealtimeProcessor not initialized");
        return nullptr;
    }

    int width = 0, height = 0;
    if (!g_realtimeProcessor->getImageDimensions(static_cast<int64_t>(handle), width, height)) {
        return nullptr;
    }

    jintArray result = env->NewIntArray(2);
    if (result == nullptr) {
        return nullptr;
    }

    jint dimensions[2] = {width, height};
    env->SetIntArrayRegion(result, 0, 2, dimensions);

    return result;
}

JNIEXPORT void JNICALL
Java_io_github_yahiaangelo_filmsimulator_util_RealtimeImageProcessor_cleanupNative(
    JNIEnv* env,
    jobject /* this */) {

    if (g_realtimeProcessor) {
        g_realtimeProcessor.reset();
        LOGI("RealtimeProcessor cleaned up");
    }
}

} // extern "C"