cmake_minimum_required(VERSION 3.22.1)
project(dolphin_gguf LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Static link into libdolphin_gguf.so; disable unused binaries & remote network deps.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build shared libraries" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Build tests" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Build examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Build server" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "Enable curl" FORCE)

# ARM64 portability & hardware backend policy (verified against vendored llama.cpp b4418):
# 1. GGML_NATIVE is OFF to avoid compiling host build-machine CPU instructions into Android binaries.
# 2. GGML_CPU_ALL_VARIANTS in vendored ggml-cpu/CMakeLists.txt only builds x86 variants and requires
#    GGML_BACKEND_DL (dynamic backend libraries), so it does not provide runtime-dispatched ARM64
#    dot-product/i8mm variants in this static build.
# 3. ggml-cpu-quants.c / ggml-cpu.c emit vdotq_s32/FP16 intrinsics unconditionally whenever
#    __ARM_FEATURE_DOTPROD / armv8.2-a+ is enabled globally, without runtime getauxval() guards.
#    Keeping baseline armv8-a on arm64-v8a avoids SIGILL crashes on ARMv8.0-A minSdk 26 devices.
# 4. Vulkan/OpenCL GPU offload is disabled because glslc shader compilation and validated mobile
#    GPU offload are not provisioned in this build; CPU execution is reported honestly.
set(GGML_NATIVE OFF CACHE BOOL "Native host optimizations" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "Enable OpenMP" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "Enable llamafile" FORCE)
set(GGML_CPU_AARCH64 ON CACHE BOOL "Enable AArch64 NEON baseline optimizations" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "Enable Vulkan backend" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "Enable OpenCL backend" FORCE)
set(GGML_CUDA OFF CACHE BOOL "Enable CUDA backend" FORCE)

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    message(STATUS "Using local vendored llama.cpp from ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp")
    add_subdirectory(llama.cpp llama_build EXCLUDE_FROM_ALL)
    set(LLAMA_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include")
else()
    message(STATUS "Vendored llama.cpp not found; falling back to FetchContent")
    include(FetchContent)
    FetchContent_Declare(
        llama_cpp
        GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
        GIT_TAG b4418
        GIT_SHALLOW TRUE
    )
    FetchContent_MakeAvailable(llama_cpp)
    set(LLAMA_INCLUDE_DIR "${llama_cpp_SOURCE_DIR}/include")
endif()

add_library(dolphin_gguf SHARED dolphin_gguf.cpp)
target_include_directories(dolphin_gguf PRIVATE ${LLAMA_INCLUDE_DIR})
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(dolphin_gguf PRIVATE -march=armv8-a)
endif()
target_link_libraries(dolphin_gguf PRIVATE llama log android)
