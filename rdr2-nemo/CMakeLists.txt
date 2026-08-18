# rdr2-nemo — TRINITY v4.0 世界观 × RDR2 单机 mod
# 支持 MSVC（Visual Studio 2013+）与 MinGW-w64。
#
# MSVC 构建：
#   cmake -B build -G "Visual Studio 17 2022" -A x64
#   cmake --build build --config Release
#
# MinGW 构建（需要 ScriptHookRDR2.dll 生成 import lib，见 Makefile）：
#   mingw32-make -f Makefile.mingw

cmake_minimum_required(VERSION 3.16)
project(rdr2-nemo CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(MSVC)
    add_compile_options(/O2 /W3 /EHsc /utf-8)
    add_definitions(-D_CRT_SECURE_NO_WARNINGS)
else()
    add_compile_options(-O2 -Wall -Wno-unused-parameter -Wno-deprecated-declarations)
endif()

add_library(nemo SHARED
    src/main.cpp
    src/nemo.cpp
)

target_include_directories(nemo PRIVATE inc)
target_link_libraries(nemo PRIVATE ws2_32)

if(MSVC)
    target_link_libraries(nemo PRIVATE lib/ScriptHookRDR2.lib)
endif()

# 输出为 .asi（RDR2 asi loader 加载）
set_target_properties(nemo PROPERTIES
    PREFIX ""
    OUTPUT_NAME "nemo"
    SUFFIX ".asi"
)
