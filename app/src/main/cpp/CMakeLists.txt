cmake_minimum_required(VERSION 3.22)

project(sadu-llama C CXX)

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
# The GPU backend only. Offloading the layers to Adreno is worth several times the prompt
# speed on the phones this runs on; the shaders need glslc on the build machine, so a build
# without it quietly stays CPU-only instead of failing.
option(SADU_VULKAN "ggml: build the Vulkan backend for the phone's GPU" ON)
if(SADU_VULKAN)
    find_program(SADU_GLSLC NAMES glslc)
    if(SADU_GLSLC AND ANDROID)
        set(GGML_VULKAN ON CACHE BOOL "" FORCE)
        message(STATUS "sadu: Vulkan backend on, shaders with ${SADU_GLSLC}")
    else()
        set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
        message(STATUS "sadu: Vulkan backend off (no glslc on this machine)")
    endif()
endif()
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LTO OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_FATAL_WARNINGS OFF CACHE BOOL "" FORCE)
# ggml is compiled for one ARM level. armv8-a is the floor that runs on every phone, but it has no
# SDOT/UDOT and no fp16 arithmetic: the Q4_K matmul then falls back to the slow path, which is most
# of the gap against an app built for a modern phone. armv8.2 with dotprod and fp16 is what every
# armv8.2 device from 2018 on has, and what a PocketPal-class build is compiled for.
# A build that must run on older hardware can pass -DsaduArmBaseline=armv8-a.
set(SADU_ARM_BASELINE "armv8.2-a+dotprod+fp16" CACHE STRING "ggml -march for arm64")
set(GGML_CPU_ARM_ARCH "${SADU_ARM_BASELINE}" CACHE STRING "" FORCE)

# GPU offload: llama.cpp's Vulkan backend. The NDK supplies the headers and the libvulkan stub
# the app links against; the build host needs glslc, because it turns the compute shaders into
# SPIR-V at build time. Gradle hands us that path in SADU_GLSLC (see app/build.gradle.kts, which
# unpacks the Ubuntu package when the host has none). Without it this stays a CPU-only build and
# Settings says so instead of pretending.
option(SADU_VULKAN "Build the Vulkan backend so layers can run on the GPU" ON)
if(SADU_GLSLC AND EXISTS "${SADU_GLSLC}")
    set(SADU_VULKAN ON)
endif()
if(SADU_VULKAN AND SADU_GLSLC AND EXISTS "${SADU_GLSLC}")
    set(GGML_VULKAN ON CACHE BOOL "" FORCE)
    set(Vulkan_GLSLC_EXECUTABLE "${SADU_GLSLC}" CACHE FILEPATH "" FORCE)
    # ggml asks for SPIRV-Headers by name; pointing _DIR at the stand-in config is enough and,
    # unlike CMAKE_PREFIX_PATH, survives into the fetched subproject.
    set(SPIRV-Headers_DIR "${CMAKE_CURRENT_SOURCE_DIR}/cmake" CACHE PATH "" FORCE)
    if(ANDROID)
        file(GLOB _ndk_host_dirs "${ANDROID_NDK}/toolchains/llvm/prebuilt/*")
        list(GET _ndk_host_dirs 0 _ndk_host)
        set(VULKAN_INCLUDE_DIR "${_ndk_host}/sysroot/usr/include" CACHE PATH "" FORCE)
        set(VULKAN_LIBRARY
                "${_ndk_host}/sysroot/usr/lib/${CMAKE_LIBRARY_ARCHITECTURE}/${ANDROID_PLATFORM_LEVEL}/libvulkan.so"
                CACHE FILEPATH "" FORCE)
    endif()
    message(STATUS "sadu: Vulkan backend ON (glslc ${SADU_GLSLC})")
else()
    set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
    message(STATUS "sadu: Vulkan backend OFF, CPU only")
endif()

FetchContent_MakeAvailable(llama_cpp)

# Android Gradle adds -g to the command line. Release libraries here are meant for the APK, not for
# a debugger, and the debug info alone is ten times the size of the code - so it comes straight
# back off. -g0 lands after -g, which is what decides it.
foreach(_target llama ggml ggml-base ggml-cpu)
    if(TARGET ${_target})
        target_compile_options(${_target} PRIVATE -g0)
    endif()
endforeach()

add_library(sadu_llama SHARED llama_jni.cpp)

target_compile_options(sadu_llama PRIVATE -O3 -fvisibility=hidden)

target_include_directories(sadu_llama PRIVATE
        ${llama_cpp_SOURCE_DIR}/include
        ${llama_cpp_SOURCE_DIR}/ggml/include)

# Static, so the APK ends up with one library instead of llama plus four ggml ones.
target_link_libraries(sadu_llama
        llama
        ggml
        android
        log)
