#!/bin/bash

# Script to compile Metal shaders into a library
SHADER_DIR="${PWD}/shared/src/iosMain/resources"
OUTPUT_DIR="${SHADER_DIR}"

echo "Compiling Metal shaders..."

# Compile individual shader files to .air format
xcrun -sdk iphoneos metal -c "${SHADER_DIR}/LUTShaders.metal" -o "${OUTPUT_DIR}/LUTShaders.air"
xcrun -sdk iphoneos metal -c "${SHADER_DIR}/LUTKernels.metal" -o "${OUTPUT_DIR}/LUTKernels.air"

# Link .air files into a .metallib library
xcrun -sdk iphoneos metallib "${OUTPUT_DIR}/LUTShaders.air" "${OUTPUT_DIR}/LUTKernels.air" -o "${OUTPUT_DIR}/default.metallib"

# Clean up intermediate files
rm -f "${OUTPUT_DIR}/*.air"

echo "Metal library compilation complete: ${OUTPUT_DIR}/default.metallib"