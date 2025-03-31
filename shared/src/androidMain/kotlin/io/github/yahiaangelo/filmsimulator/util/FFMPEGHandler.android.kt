package util

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun apply3dLutAsync(inputFile: String, lutFile: String, outputFile: String, isThumbnail: Boolean, onComplete: () -> Unit, onError: (String) -> Unit) {
    val inputFileDir = "$systemTemporaryPath/$inputFile"
    val outputFileDir = if (isThumbnail) "$systemTemporaryPath/$THUMBNAILS_DIR/$outputFile" else "$systemTemporaryPath/$outputFile"
    val lutFileDir = "$systemTemporaryPath/$lutFile"
    val outputSize = if (isThumbnail) ",scale=320:-1" else ""

    deleteFile(outputFileDir)
    withContext(Dispatchers.IO) {
        FFmpegKit.executeAsync("-i $inputFileDir -vf lut3d=$lutFileDir$outputSize -q:v 1 $outputFileDir") { session ->
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

actual suspend fun apply3dLut(
    inputFile: String,
    lutFile: String,
    outputFile: String,
    isThumbnail: Boolean
): Boolean {
    val inputFileDir = "$systemTemporaryPath/$inputFile"
    val outputFileDir = if (isThumbnail) "$systemTemporaryPath/$THUMBNAILS_DIR/$outputFile" else "$systemTemporaryPath/$outputFile"
    val lutFileDir = "$systemTemporaryPath/$lutFile"
    val outputSize = if (isThumbnail) ",scale=320:-1" else ""


    deleteFile(outputFileDir)

    return ReturnCode.isSuccess(FFmpegKit.execute("-i $inputFileDir -vf lut3d=$lutFileDir$outputSize -q:v 1 $outputFileDir")?.returnCode)
}

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

actual suspend fun applyFilters(
    command: String,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {

    withContext(Dispatchers.IO) {
        FFmpegKit.executeAsync(command) { session ->
            if (ReturnCode.isSuccess(session?.returnCode)) {
                // SUCCESS
                onComplete()

            } else if (ReturnCode.isCancel(session?.returnCode)) {
                // CANCEL
                onError("ffmpeg canceled by user")
            } else {
                // FAILURE
                onError("ffmpeg failed, unsupported file format")
            }
        }
    }
}
