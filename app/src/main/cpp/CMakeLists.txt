cmake_minimum_required(VERSION 3.22.1)
project(picsearch)

set(ncnn_DIR ${CMAKE_SOURCE_DIR}/../jni/ncnn-20260113-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

add_library(picsearch_jni SHARED
    clip_jni.cpp
)

target_compile_features(picsearch_jni PRIVATE cxx_std_17)

target_include_directories(picsearch_jni PRIVATE
    ${CMAKE_SOURCE_DIR}/../jni/ncnn-20260113-android-vulkan/${ANDROID_ABI}/include
)

target_link_libraries(picsearch_jni
    ncnn
    android
    log
    jnigraphics
)
