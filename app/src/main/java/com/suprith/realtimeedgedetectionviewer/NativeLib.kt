package com.suprith.realtimeedgedetectionviewer

object NativeLib {
    init {
        // load OpenCV native lib (from OpenCV Android SDK) first
        System.loadLibrary("opencv_java4")
        // then load our native library
        System.loadLibrary("native-lib")
    }

    // Convert NV21 -> processed ARGB pixel array (int[] in ARGB_8888)
    external fun processNV21(nv21: ByteArray, width: Int, height: Int): IntArray
}