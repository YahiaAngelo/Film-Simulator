package util


expect suspend fun apply3dLut(inputFile: String, lutFile: String, outputFile: String, onComplete: () -> Unit)