cmake_minimum_required(VERSION 3.18.1)
project("filmsimulator")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enable SIMD optimizations
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math")

# Add ARM NEON support for ARM architectures
if(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon -mfloat-abi=softfp")
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
    # ARM64 has NEON by default
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a")
elseif(${ANDROID_ABI} STREQUAL "x86" OR ${ANDROID_ABI} STREQUAL "x86_64")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -msse4.2 -mavx2")
endif()

# Enable OpenMP for parallelization
find_package(OpenMP)
if(OpenMP_CXX_FOUND)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${OpenMP_CXX_FLAGS}")
endif()

# Find required libraries
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(android-lib android)

# Add the native library
add_library(filmsimulator SHARED
        lut_processor.cpp
        image_processor.cpp
        film_grain.cpp
        jni_bridge.cpp)

# Link libraries
target_link_libraries(filmsimulator
        ${log-lib}
        ${jnigraphics-lib}
        ${android-lib})

# Link OpenMP if found
if(OpenMP_CXX_FOUND)
    target_link_libraries(filmsimulator OpenMP::OpenMP_CXX)
endif()

# Include directories
target_include_directories(filmsimulator PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR})