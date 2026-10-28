cmake_minimum_required(VERSION 3.22)

project(jarvis-llama C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp, pinned. Only what an on-device chat needs is built: no server, no tools, no tests,
# no HTTPS, and the CPU backend only. Bump the tag and the hash together.
set(LLAMA_TAG v0.7.0)
set(LLAMA_SHA256 55d67acf3ad2ad747a9bbeb75f9f0e173dbcc0d1753a471038a90c819421c5e1)

include(FetchContent)
FetchContent_Declare(
        llama_cpp
        URL https://github.com/ggml-org/llama.cpp/archive/refs/tags/${LLAMA_TAG}.tar.gz
        URL_HASH SHA256=${LLAMA_SHA256}
)

# Anything left on pulls in curl/openssl or the example binaries, none of which belong in the APK.
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_HTML OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS OFF CACHE BOOL "" FORCE)
set(LLAMA_LLGUIDANCE OFF CACHE BOOL "" FORCE)
set(LLAMA_USE_PREBUILT_UI OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LTO OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_FATAL_WARNINGS OFF CACHE BOOL "" FORCE)
# The APK ships arm64-v8a only, so asking ggml for that baseline keeps the library small.
set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "" FORCE)

FetchContent_MakeAvailable(llama_cpp)

# Android Gradle adds -g to the command line. Release libraries here are meant for the APK, not for
# a debugger, and the debug info alone is ten times the size of the code - so it comes straight
# back off. -g0 lands after -g, which is what decides it.
foreach(_target llama ggml ggml-base ggml-cpu)
    if(TARGET ${_target})
        target_compile_options(${_target} PRIVATE -g0)
    endif()
endforeach()

add_library(jarvis_llama SHARED llama_jni.cpp)

target_compile_options(jarvis_llama PRIVATE -O3 -fvisibility=hidden)

target_include_directories(jarvis_llama PRIVATE
        ${llama_cpp_SOURCE_DIR}/include
        ${llama_cpp_SOURCE_DIR}/ggml/include)

# Static, so the APK ends up with one library instead of llama plus four ggml ones.
target_link_libraries(jarvis_llama
        llama
        ggml
        android
        log)
