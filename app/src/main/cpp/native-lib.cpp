#include <jni.h>
#include <string>
#include <android/log.h>
#include <opencv2/opencv.hpp>

#define LOG_TAG "native-lib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace cv;

extern "C" JNIEXPORT jintArray JNICALL
Java_com_suprith_realtimeedgedetectionviewer_NativeLib_processNV21(JNIEnv *env, jobject thiz, jbyteArray nv21Array, jint width, jint height) {


    jbyte *nv21 = env->GetByteArrayElements(nv21Array, NULL);
    if (nv21 == NULL) return NULL;

    int yuvSize = width * height + (width * height) / 2;
    // Create Mat with NV21 layout: height + height/2 rows, width cols
    Mat yuv(height + height/2, width, CV_8UC1, (unsigned char *) nv21);

    Mat rgba;
    // NV21 -> RGBA
    cvtColor(yuv, rgba, COLOR_YUV2RGBA_NV21);

    // convert to grayscale
    Mat gray;
    cvtColor(rgba, gray, COLOR_RGBA2GRAY);

    // Canny edge detection
    Mat edges;
    Canny(gray, edges, 50, 150);

    Mat out;
    cvtColor(edges, out, COLOR_GRAY2RGBA);

    // Prepare jintArray to return
    jintArray ret = env->NewIntArray(width * height);
    jint *retPixels = env->GetIntArrayElements(ret, NULL);

    // pack as ARGB_8888 (0xAARRGGBB)
    for (int y = 0; y < height; ++y) {
        const Vec4b* row = out.ptr<Vec4b>(y);
        for (int x = 0; x < width; ++x) {
            Vec4b px = row[x]; // px[0]=R px[1]=G px[2]=B px[3]=A
            int r = px[0];
            int g = px[1];
            int b = px[2];
            int a = px[3];
            jint color = ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
            retPixels[y * width + x] = color;
        }
    }

    env->ReleaseIntArrayElements(ret, retPixels, 0);
    env->ReleaseByteArrayElements(nv21Array, nv21, JNI_ABORT);
    return ret;
}
