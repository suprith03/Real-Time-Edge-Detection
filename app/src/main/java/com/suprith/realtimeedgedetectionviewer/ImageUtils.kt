package com.suprith.realtimeedgedetectionviewer

import android.media.Image
import android.util.Log
object ImageUtils {
    private const val TAG = "ImageUtils"

    /**
     * Convert an Image (YUV_420_888) -> NV21 byte array (Y + interleaved VU).
     * This implementation is robust across devices:
     * - handles rowStride != width
     * - handles pixelStride != 1 (UV interleaving)
     * - uses absolute ByteBuffer.get(index) to avoid BufferUnderflow
     */
    fun imageToNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        // Debug log to help diagnose device quirks (remove or comment out in production)
        Log.d(TAG, "imageToNV21: w=$width h=$height yRowStride=$yRowStride uRowStride=$uRowStride vRowStride=$vRowStride uPixStride=$uPixelStride vPixStride=$vPixelStride")

        // 1) Copy Y plane (row by row if rowStride != width)
        var pos = 0
        if (yRowStride == width) {
            // fast path
            yBuffer.get(nv21, 0, ySize)
            pos += ySize
        } else {
            val row = ByteArray(yRowStride)
            for (rowIndex in 0 until height) {
                val offset = rowIndex * yRowStride
                // Read the entire row (may contain padding)
                yBuffer.position(offset)
                val toRead = minOf(yRowStride, yBuffer.limit() - offset)
                // read into row; if buffer doesn't have full row, read available
                yBuffer.get(row, 0, toRead)
                // copy only the width bytes (exclude padding)
                System.arraycopy(row, 0, nv21, pos, width)
                pos += width
            }
        }

        // 2) Interleave V and U to NV21 (expected order: V then U)
        // We'll read absolute indices from duplicated buffers to avoid messing positions
        val chromaHeight = height / 2
        val chromaWidth = width / 2

        val uBuf = uBuffer.duplicate()
        val vBuf = vBuffer.duplicate()

        val uLimit = uBuf.limit()
        val vLimit = vBuf.limit()

        for (rowIndex in 0 until chromaHeight) {
            val uRowStart = rowIndex * uRowStride
            val vRowStart = rowIndex * vRowStride
            for (colIndex in 0 until chromaWidth) {
                val uIndex = uRowStart + colIndex * uPixelStride
                val vIndex = vRowStart + colIndex * vPixelStride

                val uByte: Byte = if (uIndex >= 0 && uIndex < uLimit) uBuf.get(uIndex) else 0
                val vByte: Byte = if (vIndex >= 0 && vIndex < vLimit) vBuf.get(vIndex) else 0

                // NV21 expects V then U
                if (pos < nv21.size) {
                    nv21[pos++] = vByte
                }
                if (pos < nv21.size) {
                    nv21[pos++] = uByte
                }
            }
        }

        return nv21
    }

    fun nv21ToRgba(nv21: ByteArray, width: Int, height: Int): IntArray {
        val frameSize = width * height
        val rgba = IntArray(frameSize)

        var yIndex: Int
        var uvIndex: Int
        var y: Int
        var u: Int
        var v: Int
        var r: Int
        var g: Int
        var b: Int

        for (j in 0 until height) {
            yIndex = j * width
            uvIndex = frameSize + (j shr 1) * width

            for (i in 0 until width) {
                y = 0xFF and nv21[yIndex + i].toInt()
                v = 0xFF and nv21[uvIndex + (i and -2)].toInt()
                u = 0xFF and nv21[uvIndex + (i and -2) + 1].toInt()

                // Convert YUV -> RGB
                val y1192 = 1192 * (y - 16).coerceAtLeast(0)
                val rTmp = (y1192 + 1634 * (v - 128))
                val gTmp = (y1192 - 833 * (v - 128) - 400 * (u - 128))
                val bTmp = (y1192 + 2066 * (u - 128))

                r = (rTmp shr 10).coerceIn(0, 255)
                g = (gTmp shr 10).coerceIn(0, 255)
                b = (bTmp shr 10).coerceIn(0, 255)

                rgba[yIndex + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return rgba
    }
}
