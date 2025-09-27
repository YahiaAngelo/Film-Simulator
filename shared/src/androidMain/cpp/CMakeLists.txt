cmake_minimum_required(VERSION 3.18.1)
project("filmsimulator")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

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

# Include directories
target_include_directories(filmsimulator PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR})