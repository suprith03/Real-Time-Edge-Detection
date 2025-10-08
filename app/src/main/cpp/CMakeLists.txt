cmake_minimum_required(VERSION 3.10)
project("realtimeedgedetectionviewer")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Path to your OpenCV Android SDK
set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/../../../../OpenCV-android-sdk/sdk/native/jni)

add_library(native-lib SHARED native-lib.cpp)

# import OpenCV shared lib
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES
        IMPORTED_LOCATION ${OpenCV_DIR}/../libs/${ANDROID_ABI}/libopencv_java4.so
)

# ✅ import the Android NDK C++ shared runtime (this is the missing library)
find_library(libcpp_shared c++_shared)
find_library(log-lib log)

# include OpenCV headers
include_directories(${OpenCV_DIR}/include)

# ✅ link everything together
target_link_libraries(
        native-lib
        ${libcpp_shared}          # <--- add this line
        opencv_java4
        ${log-lib}
)
