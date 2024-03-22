package util

import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun apply3dLut(inputFile: String, lutFile: String, outputFile: String, onComplete: () -> Unit) {
    val inputFileDir = "$systemTemporaryPath/$inputFile"
    val outputFileDir = "$systemTemporaryPath/$outputFile"
    val lutFileDir = "$systemTemporaryPath/$lutFile"

    deleteFile(outputFileDir)
    withContext(Dispatchers.IO) {
        FFmpegKit.executeAsync("-i $inputFileDir -vf lut3d=$lutFileDir -c:a copy $outputFileDir") {
            onComplete()
        }
    }
}