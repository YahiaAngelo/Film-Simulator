# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Film Simulator is a cross-platform mobile app built with Kotlin Multiplatform and Compose UI. It applies film-like LUTs (Look-Up Tables) and image adjustments to photos on Android and iOS.

**Key Migration**: The project recently migrated from FFmpeg-kit to platform-native implementations:
- **Android**: Custom C++ implementation using Android NDK
- **iOS**: Core Graphics-based implementation (simplified from original Metal approach)

## Build Commands

### Android
```bash
# Build shared module
./gradlew shared:assembleDebug

# Build Android app
./gradlew androidApp:assembleDebug

# Install on device
./gradlew androidApp:installDebug
```

### iOS
```bash
# Build iOS framework
./gradlew shared:linkDebugFrameworkIosSimulatorArm64

# Open iOS project (then build in Xcode)
open iosApp/iosApp.xcodeproj
```

### Cross-platform
```bash
# Clean all
./gradlew clean

# Build all targets
./gradlew build
```

## Architecture

### Multiplatform Structure
- **`shared/src/commonMain/`**: Shared business logic, UI, and utilities
- **`shared/src/androidMain/`**: Android-specific implementations + C++ native code
- **`shared/src/iosMain/`**: iOS-specific implementations
- **`androidApp/`**: Android app entry point
- **`iosApp/`**: iOS app entry point

### Core Architecture Patterns
- **MVVM**: Uses Voyager ScreenModels with Kotlin flows for reactive state management
- **Repository Pattern**: Data layer with local (SQLDelight) and network (Ktor) sources
- **Dependency Injection**: Koin DI manages object creation across all modules

### Critical Native Components
The app's core image processing happens in platform-native code:

**Android C++ (shared/src/androidMain/cpp/)**:
- `lut_processor.cpp`: 3D LUT processing with trilinear interpolation
- `image_processor.cpp`: Bitmap manipulation and JNI bridge
- `film_grain.cpp`: Grain effect generation
- `jni_bridge.cpp`: Kotlin ↔ C++ interface
- `NativeLUTProcessor.kt`: Kotlin wrapper for C++ functions

**iOS Implementation (shared/src/iosMain/)**:
- `MetalLUTProcessor.kt`: Core Graphics-based image processing
- Handles thumbnail creation and basic image transformations

### Key Modules
- **`screens/home/HomeScreenModel`**: Main app logic, image processing orchestration
- **`data/source/FilmRepository`**: Manages LUT data from local DB and network
- **`lut/LutDownloadManager`**: Downloads and caches LUT files
- **`image/ImageRenderer`**: Compose UI rendering with runtime shaders
- **`util/FFMPEGHandler`**: Platform abstraction for native image processing

### Data Flow
1. User selects image via Peekaboo Image Picker
2. Image saved to temporary cache (FileHandler)
3. HomeScreenModel coordinates LUT application via FFMPEGHandler
4. Platform-specific native code processes image
5. Result rendered in Compose UI via ImageRenderer
6. Export saves processed image to gallery

## Development Notes

### Native Code Development
- Android C++ requires CMake 3.22.1+
- Changes to .cpp files need `./gradlew clean` to rebuild native libs
- Use Android NDK logging macros (LOGI, LOGE) for debugging
- iOS implementation is Core Graphics-based, avoiding complex Metal interop

### Image Processing Pipeline
The app processes images through this flow:
1. **Load**: Image loaded from file system
2. **Cache**: Stored in app's temporary directory
3. **Process**: Native code applies LUT transformations
4. **Thumbnail**: Generated for UI preview (320px width)
5. **Export**: Full resolution saved to device gallery

### Platform Considerations
- **Android**: Minimum SDK 24, supports RGBA_8888 bitmaps only
- **iOS**: Uses UIKit for image handling, UIImage for processing
- **Shared**: Compose Resources manage assets consistently

### Common Issues
- **Memory**: Large images need careful bitmap recycling on Android
- **Threading**: Image processing runs on Dispatchers.IO
- **Permissions**: Gallery access requires platform-specific handling
- **Build**: Native code changes require clean builds