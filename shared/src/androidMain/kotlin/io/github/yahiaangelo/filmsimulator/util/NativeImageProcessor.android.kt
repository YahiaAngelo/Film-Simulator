package io.github.yahiaangelo.filmsimulator.util

actual class NativeImageProcessor {

    actual fun processImageWithLUT(
        inputPath: String,
        outputPath: String,
        lutPath: String,
        createThumbnail: Boolean
    ): Boolean {
        // Android already uses native C++ implementation through JNI
        // This is handled by the existing NativeLUTProcessor
        // This actual implementation is just a placeholder

        // The actual processing happens in FFMPEGHandler.android.kt
        // which calls the native C++ code

        return false // Let FFMPEGHandler handle it
    }

    actual fun isNativeProcessingAvailable(): Boolean {
        // Android native library is always available
        return true
    }
}
