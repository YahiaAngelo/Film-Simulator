#include "image_processor.h"
#include "lut_processor.h"
#include "image_adjustments.h"
#include <android/log.h>
#include <vector>
#include <fstream>
#include <memory>
#include <cstring>

#define LOG_TAG "ImageProcessor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

bool ImageProcessor::processImageFile(
    JNIEnv* env,
    const std::string& inputPath,
    const std::string& outputPath,
    const std::string& lutPath,
    bool createThumbnail) {

    // This would require additional image decoding/encoding library
    // For now, we'll focus on the bitmap processing version
    // which can be called from Kotlin using Android's BitmapFactory

    LOGI("Processing image file: %s", inputPath.c_str());
    // Implementation would go here with proper image I/O
    return false;
}

bool ImageProcessor::processBitmap(
    JNIEnv* env,
    jobject inputBitmap,
    jobject outputBitmap,
    const std::string& lutPath) {

    AndroidBitmapInfo inputInfo;
    AndroidBitmapInfo outputInfo;
    void* inputPixels;
    void* outputPixels;

    // Get bitmap info
    if (AndroidBitmap_getInfo(env, inputBitmap, &inputInfo) < 0) {
        LOGE("Failed to get input bitmap info");
        return false;
    }

    if (AndroidBitmap_getInfo(env, outputBitmap, &outputInfo) < 0) {
        LOGE("Failed to get output bitmap info");
        return false;
    }

    LOGI("Input bitmap: %dx%d, stride: %d, format: %d",
         inputInfo.width, inputInfo.height, inputInfo.stride, inputInfo.format);
    LOGI("Output bitmap: %dx%d, stride: %d, format: %d",
         outputInfo.width, outputInfo.height, outputInfo.stride, outputInfo.format);

    // Check format
    if (inputInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Input bitmap format must be RGBA_8888, got: %d", inputInfo.format);
        return false;
    }

    if (outputInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Output bitmap format must be RGBA_8888, got: %d", outputInfo.format);
        return false;
    }

    // Check dimensions match
    if (inputInfo.width != outputInfo.width || inputInfo.height != outputInfo.height) {
        LOGE("Bitmap dimensions mismatch: input %dx%d, output %dx%d",
             inputInfo.width, inputInfo.height, outputInfo.width, outputInfo.height);
        return false;
    }

    // Lock pixels
    if (AndroidBitmap_lockPixels(env, inputBitmap, &inputPixels) < 0) {
        LOGE("Failed to lock input bitmap pixels");
        return false;
    }

    if (AndroidBitmap_lockPixels(env, outputBitmap, &outputPixels) < 0) {
        AndroidBitmap_unlockPixels(env, inputBitmap);
        LOGE("Failed to lock output bitmap pixels");
        return false;
    }

    // Process the image with LUT (thumbnail scaling handled at Kotlin level)
    bool success = processImageWithLUT(
        static_cast<uint8_t*>(inputPixels),
        static_cast<uint8_t*>(outputPixels),
        inputInfo.width,
        inputInfo.height,
        lutPath,
        false
    );

    // Unlock pixels
    AndroidBitmap_unlockPixels(env, inputBitmap);
    AndroidBitmap_unlockPixels(env, outputBitmap);

    return success;
}

void ImageProcessor::scaleImage(
    uint8_t* input,
    uint8_t* output,
    int srcWidth,
    int srcHeight,
    int dstWidth,
    int dstHeight) {

    // Simple bilinear interpolation for scaling
    float xRatio = static_cast<float>(srcWidth) / dstWidth;
    float yRatio = static_cast<float>(srcHeight) / dstHeight;

    for (int y = 0; y < dstHeight; ++y) {
        for (int x = 0; x < dstWidth; ++x) {
            float srcX = x * xRatio;
            float srcY = y * yRatio;

            int x0 = static_cast<int>(srcX);
            int y0 = static_cast<int>(srcY);
            int x1 = std::min(x0 + 1, srcWidth - 1);
            int y1 = std::min(y0 + 1, srcHeight - 1);

            float fx = srcX - x0;
            float fy = srcY - y0;

            for (int c = 0; c < 4; ++c) {
                float p00 = input[(y0 * srcWidth + x0) * 4 + c];
                float p01 = input[(y0 * srcWidth + x1) * 4 + c];
                float p10 = input[(y1 * srcWidth + x0) * 4 + c];
                float p11 = input[(y1 * srcWidth + x1) * 4 + c];

                float p0 = p00 * (1 - fx) + p01 * fx;
                float p1 = p10 * (1 - fx) + p11 * fx;
                float p = p0 * (1 - fy) + p1 * fy;

                output[(y * dstWidth + x) * 4 + c] = static_cast<uint8_t>(p);
            }
        }
    }
}

bool ImageProcessor::processBitmapWithAdjustments(
    JNIEnv* env,
    jobject inputBitmap,
    jobject outputBitmap,
    const ImageAdjustments& adjustments) {

    AndroidBitmapInfo inputInfo;
    AndroidBitmapInfo outputInfo;
    void* inputPixels;
    void* outputPixels;

    // Get bitmap info
    if (AndroidBitmap_getInfo(env, inputBitmap, &inputInfo) < 0) {
        LOGE("Failed to get input bitmap info");
        return false;
    }

    if (AndroidBitmap_getInfo(env, outputBitmap, &outputInfo) < 0) {
        LOGE("Failed to get output bitmap info");
        return false;
    }

    // Check format
    if (inputInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        outputInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format must be RGBA_8888");
        return false;
    }

    // Check dimensions match
    if (inputInfo.width != outputInfo.width || inputInfo.height != outputInfo.height) {
        LOGE("Bitmap dimensions mismatch");
        return false;
    }

    // Lock pixels
    if (AndroidBitmap_lockPixels(env, inputBitmap, &inputPixels) < 0) {
        LOGE("Failed to lock input bitmap pixels");
        return false;
    }

    if (AndroidBitmap_lockPixels(env, outputBitmap, &outputPixels) < 0) {
        AndroidBitmap_unlockPixels(env, inputBitmap);
        LOGE("Failed to lock output bitmap pixels");
        return false;
    }

    // Copy input to output first
    std::memcpy(outputPixels, inputPixels,
                inputInfo.width * inputInfo.height * 4);

    // Apply adjustments to output
    ImageAdjustmentProcessor::applyAdjustmentsToImage(
        static_cast<uint8_t*>(outputPixels),
        inputInfo.width,
        inputInfo.height,
        adjustments
    );

    // Unlock pixels
    AndroidBitmap_unlockPixels(env, inputBitmap);
    AndroidBitmap_unlockPixels(env, outputBitmap);

    LOGI("Successfully applied adjustments to %dx%d image",
         inputInfo.width, inputInfo.height);

    return true;
}

bool ImageProcessor::processBitmapWithLutAndAdjustments(
    JNIEnv* env,
    jobject inputBitmap,
    jobject outputBitmap,
    const std::string& lutPath,
    const ImageAdjustments& adjustments) {

    AndroidBitmapInfo inputInfo;
    AndroidBitmapInfo outputInfo;
    void* inputPixels;
    void* outputPixels;

    // Get bitmap info
    if (AndroidBitmap_getInfo(env, inputBitmap, &inputInfo) < 0) {
        LOGE("Failed to get input bitmap info");
        return false;
    }

    if (AndroidBitmap_getInfo(env, outputBitmap, &outputInfo) < 0) {
        LOGE("Failed to get output bitmap info");
        return false;
    }

    // Check format
    if (inputInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        outputInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format must be RGBA_8888");
        return false;
    }

    // Check dimensions match
    if (inputInfo.width != outputInfo.width || inputInfo.height != outputInfo.height) {
        LOGE("Bitmap dimensions mismatch");
        return false;
    }

    // Lock pixels
    if (AndroidBitmap_lockPixels(env, inputBitmap, &inputPixels) < 0) {
        LOGE("Failed to lock input bitmap pixels");
        return false;
    }

    if (AndroidBitmap_lockPixels(env, outputBitmap, &outputPixels) < 0) {
        AndroidBitmap_unlockPixels(env, inputBitmap);
        LOGE("Failed to lock output bitmap pixels");
        return false;
    }

    // First apply LUT
    bool success = processImageWithLUT(
        static_cast<uint8_t*>(inputPixels),
        static_cast<uint8_t*>(outputPixels),
        inputInfo.width,
        inputInfo.height,
        lutPath,
        false
    );

    // Then apply adjustments on top of the LUT result
    if (success) {
        ImageAdjustmentProcessor::applyAdjustmentsToImage(
            static_cast<uint8_t*>(outputPixels),
            inputInfo.width,
            inputInfo.height,
            adjustments
        );
    }

    // Unlock pixels
    AndroidBitmap_unlockPixels(env, inputBitmap);
    AndroidBitmap_unlockPixels(env, outputBitmap);

    if (success) {
        LOGI("Successfully applied LUT and adjustments to %dx%d image",
             inputInfo.width, inputInfo.height);
    }

    return success;
}