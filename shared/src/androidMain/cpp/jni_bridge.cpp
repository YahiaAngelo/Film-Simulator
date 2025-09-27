#include <jni.h>
#include <string>
#include <android/log.h>
#include "image_processor.h"
#include "film_grain.h"
#include "lut_processor.h"

#define LOG_TAG "FilmSimulatorJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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

} // extern "C"