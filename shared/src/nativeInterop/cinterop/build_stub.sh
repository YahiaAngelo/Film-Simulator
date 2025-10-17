#!/bin/bash
# Build XCFramework from Objective-C stub
# Creates separate frameworks for device and simulator

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build"
IOS_APP_DIR="$(cd "$SCRIPT_DIR/../../../.." && pwd)/iosApp/iosApp"

mkdir -p "$BUILD_DIR"

echo "Building NativeProcessorBridge stub library..."
echo "Script dir: $SCRIPT_DIR"
echo "iOS app dir: $IOS_APP_DIR"

# Compile for arm64 device
echo "Building for arm64 (device)..."
clang -c \
  -target arm64-apple-ios16.0 \
  -isysroot "$(xcrun --sdk iphoneos --show-sdk-path)" \
  -I"$IOS_APP_DIR" \
  -fobjc-arc \
  "$SCRIPT_DIR/NativeProcessorBridge.m" \
  -o "$BUILD_DIR/NativeProcessorBridge_arm64.o"

# Create device library
echo "Creating device library..."
ar rcs "$BUILD_DIR/libNativeProcessorBridge_arm64.a" \
  "$BUILD_DIR/NativeProcessorBridge_arm64.o"

# Compile for arm64 simulator
echo "Building for arm64 (simulator)..."
clang -c \
  -target arm64-apple-ios16.0-simulator \
  -isysroot "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
  -I"$IOS_APP_DIR" \
  -fobjc-arc \
  "$SCRIPT_DIR/NativeProcessorBridge.m" \
  -o "$BUILD_DIR/NativeProcessorBridge_sim_arm64.o"

# Compile for x86_64 simulator
echo "Building for x86_64 (simulator)..."
clang -c \
  -target x86_64-apple-ios16.0-simulator \
  -isysroot "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
  -I"$IOS_APP_DIR" \
  -fobjc-arc \
  "$SCRIPT_DIR/NativeProcessorBridge.m" \
  -o "$BUILD_DIR/NativeProcessorBridge_x86_64.o"

# Create simulator fat library
echo "Creating simulator fat library..."
ar rcs "$BUILD_DIR/libNativeProcessorBridge_sim_arm64.a" "$BUILD_DIR/NativeProcessorBridge_sim_arm64.o"
ar rcs "$BUILD_DIR/libNativeProcessorBridge_x86_64.a" "$BUILD_DIR/NativeProcessorBridge_x86_64.o"

lipo -create \
  "$BUILD_DIR/libNativeProcessorBridge_sim_arm64.a" \
  "$BUILD_DIR/libNativeProcessorBridge_x86_64.a" \
  -output "$BUILD_DIR/libNativeProcessorBridge_simulator.a"

# For Kotlin/Native cinterop, we'll use the device library as default
# Xcode will handle selecting the right architecture
cp "$BUILD_DIR/libNativeProcessorBridge_arm64.a" "$BUILD_DIR/libNativeProcessorBridge.a"

echo "✅ Static libraries built:"
echo "   - Device (arm64): $BUILD_DIR/libNativeProcessorBridge_arm64.a"
echo "   - Simulator (universal): $BUILD_DIR/libNativeProcessorBridge_simulator.a"
echo "   - Default (for cinterop): $BUILD_DIR/libNativeProcessorBridge.a"
