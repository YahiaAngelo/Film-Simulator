#!/bin/bash
# Build static library from Objective-C stub

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build"
IOS_APP_DIR="$(cd "$SCRIPT_DIR/../../../.." && pwd)/iosApp/iosApp"

mkdir -p "$BUILD_DIR"

echo "Building NativeProcessorBridge stub library..."
echo "Script dir: $SCRIPT_DIR"
echo "iOS app dir: $IOS_APP_DIR"

# Compile for simulator
clang -c \
  -target arm64-apple-ios-simulator \
  -isysroot "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
  -mios-simulator-version-min=16.0 \
  -I"$IOS_APP_DIR" \
  -fobjc-arc \
  "$SCRIPT_DIR/NativeProcessorBridge.m" \
  -o "$BUILD_DIR/NativeProcessorBridge_sim.o"

# Create static library
ar rcs "$BUILD_DIR/libNativeProcessorBridge.a" "$BUILD_DIR/NativeProcessorBridge_sim.o"

echo "✅ Static library built: $BUILD_DIR/libNativeProcessorBridge.a"
