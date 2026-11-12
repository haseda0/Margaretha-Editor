cmake_minimum_required(VERSION 3.22.1)
project(margaretha_whisper C CXX)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
# Source is fetched by the Gradle task downloadWhisperSource (app/build.gradle.kts).
add_subdirectory(${WHISPER_DIR} whisper-build)

add_library(margaretha_whisper SHARED whisper_jni.cpp)
target_link_libraries(margaretha_whisper PRIVATE whisper log)
target_compile_options(margaretha_whisper PRIVATE -O3)
