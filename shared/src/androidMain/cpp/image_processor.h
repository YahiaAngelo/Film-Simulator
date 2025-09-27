#ifndef IMAGE_PROCESSOR_H
#define IMAGE_PROCESSOR_H

#include <jni.h>
#include <android/bitmap.h>
#include <string>

// Image processing utilities
class ImageProcessor {
public:
    // Load image from file path and apply LUT
    static bool processImageFile(
        JNIEnv* env,
        const std::string& inputPath,
        const std::string& outputPath,
        const std::string& lutPath,
        bool createThumbnail = false
    );

    // Process bitmap directly
    static bool processBitmap(
        JNIEnv* env,
        jobject inputBitmap,
        jobject outputBitmap,
        const std::string& lutPath
    );

    // Scale image for thumbnail
    static void scaleImage(
        uint8_t* input,
        uint8_t* output,
        int srcWidth,
        int srcHeight,
        int dstWidth,
        int dstHeight
    );
};

#endif // IMAGE_PROCESSOR_H