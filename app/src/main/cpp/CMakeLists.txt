cmake_minimum_required(VERSION 3.22.1)
project(dolphin_gguf LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build shared libraries" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Build tests" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Build examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Build server" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "Enable curl" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "Native optimizations" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "Enable OpenMP" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "Enable llamafile" FORCE)

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
target_link_libraries(dolphin_gguf PRIVATE llama log android)
