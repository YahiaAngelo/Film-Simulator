package util

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun apply3dLut(inputFile: String, lutFile: String, outputFile: String, onComplete: () -> Unit, onError: (String) -> Unit) {
    val inputFileDir = "$systemTemporaryPath/$inputFile"
    val outputFileDir = "$systemTemporaryPath/$outputFile"
    val lutFileDir = "$systemTemporaryPath/$lutFile"

    deleteFile(outputFileDir)
    withContext(Dispatchers.IO) {
        FFmpegKit.executeAsync("-i $inputFileDir -vf lut3d=$lutFileDir -q:v 1 $outputFileDir") { session ->
            if (ReturnCode.isSuccess(session.returnCode)) {
                // SUCCESS
                onComplete()

            } else if (ReturnCode.isCancel(session.returnCode)) {
                // CANCEL
                onError("ffmpeg canceled by user")
            } else {
                // FAILURE
                onError("ffmpeg failed, unsupported file format")
            }
        }
    }
}