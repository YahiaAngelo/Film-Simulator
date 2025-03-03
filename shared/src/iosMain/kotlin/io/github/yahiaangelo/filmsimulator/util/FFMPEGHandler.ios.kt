package util

import cocoapods.ffmpeg_kit_ios_min.FFmpegKit
import cocoapods.ffmpeg_kit_ios_min.ReturnCode
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

@OptIn(ExperimentalForeignApi::class)
actual suspend fun apply3dLut(inputFile: String, lutFile: String, outputFile: String, onComplete: () -> Unit, onError: (String) -> Unit) {
    val inputFileDir = "$systemTemporaryPath/$inputFile"
    val outputFileDir = "$systemTemporaryPath/$outputFile"
    val lutFileDir = "$systemTemporaryPath/$lutFile"

    deleteFile(outputFileDir)
    withContext(Dispatchers.IO) {
        FFmpegKit.executeAsync("-i $inputFileDir -vf lut3d=$lutFileDir -q:v 1 $outputFileDir") { session ->
            if (ReturnCode.isSuccess(session?.getReturnCode())) {
                // SUCCESS
                onComplete()

            } else if (ReturnCode.isCancel(session?.getReturnCode())) {
                // CANCEL
                onError("ffmpeg canceled by user")
            } else {
                // FAILURE
                onError("ffmpeg failed, unsupported file format")
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun addFilmGrain(
    inputFile: String,
    outputFile: String,
    intensity: Float,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    val inputFileDir = "$systemTemporaryPath/$inputFile"
    val outputFileDir = "$systemTemporaryPath/$outputFile"

    deleteFile(outputFileDir)
    withContext(Dispatchers.IO) {
        FFmpegKit.executeAsync("-i $inputFileDir -vf noise=c0s=$intensity:c0f=t+u -q:v 1 $outputFileDir") { session ->
            if (session != null) {
                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    // SUCCESS
                    onComplete()

                } else if (ReturnCode.isCancel(session.getReturnCode())) {
                    // CANCEL
                    onError("ffmpeg canceled by user")
                } else {
                    // FAILURE
                    onError("ffmpeg failed, unsupported file format")
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun applyFilters(
    command: String,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {

    withContext(Dispatchers.IO) {
        FFmpegKit.executeAsync(command) { session ->
            if (ReturnCode.isSuccess(session?.getReturnCode())) {
                // SUCCESS
                onComplete()

            } else if (ReturnCode.isCancel(session?.getReturnCode())) {
                // CANCEL
                onError("ffmpeg canceled by user")
            } else {
                // FAILURE
                onError("ffmpeg failed, unsupported file format")
            }
        }
    }
}