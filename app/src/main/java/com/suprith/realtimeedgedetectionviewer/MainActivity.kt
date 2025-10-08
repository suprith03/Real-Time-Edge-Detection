package com.suprith.realtimeedgedetectionviewer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.ArrayDeque

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CAMERA = 101
    }

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: EdgeRenderer
    private lateinit var tvFps: TextView
    private lateinit var btnToggle: Button

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraHandler: Handler? = null
    private var cameraThread: HandlerThread? = null

    @Volatile
    private var showRaw = false

    private var wsClient: FrameWebSocketClient? = null

    // FPS tracking
    private val frameTimestamps = ArrayDeque<Long>()
    private var lastFpsUpdate = 0L
    private var smoothedFps = 0.0
    private val fpsWindow = 30

    private lateinit var btnSendFrame: Button
    private var lastProcessedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        glView = findViewById(R.id.glSurfaceView)
        tvFps = findViewById(R.id.tvFps)
        btnToggle = findViewById(R.id.btnToggle)
        btnSendFrame = findViewById(R.id.btnSendFrame)

        glView.setEGLContextClientVersion(2)
        renderer = EdgeRenderer()
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        btnToggle.setOnClickListener {
            showRaw = !showRaw
            btnToggle.text = if (showRaw) "Show Edge" else "Show Raw"
        }

        btnSendFrame.setOnClickListener {
            val bmp = lastProcessedBitmap
            if (bmp != null) {
                Log.i(TAG, "📤 Sending frame manually via WebSocket...")
                val stream = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 60, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                wsClient?.sendFrame(base64)
                Log.i(TAG, "✅ Frame sent (${base64.length} bytes)")
                Log.w(TAG, base64)
            } else {
                Log.w(TAG, "⚠️ No frame available yet.")
            }
        }


        Thread {
            try {
                val relayUri = URI("ws://192.168.1.9:8081") // replace with your machine IP
                wsClient = FrameWebSocketClient(relayUri)
                wsClient?.connect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA
            )
        } else {
            startCameraThread()
            openCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraThread()
                openCamera()
            } else {
                finish()
            }
        }
    }

    private fun startCameraThread() {
        cameraThread = HandlerThread("CameraThread").also { it.start() }
        cameraHandler = Handler(cameraThread!!.looper)
    }

    private fun stopCameraThread() {
        cameraThread?.quitSafely()
        cameraThread?.join()
        cameraThread = null
        cameraHandler = null
    }

    private fun openCamera() {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val camId = cm.cameraIdList[0]
            val characteristics = cm.getCameraCharacteristics(camId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888)

            val chosen: Size = sizes?.firstOrNull { it.width <= 1280 && it.height <= 720 }
                ?: Size(640, 480)
            Log.d(TAG, "Using camera size: ${chosen.width}x${chosen.height}")

            imageReader = ImageReader.newInstance(
                chosen.width,
                chosen.height,
                ImageFormat.YUV_420_888,
                2
            )
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                handleImage(image)
                image.close()
            }, cameraHandler)

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) return

            cm.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.i(TAG, "Camera opened")
                    cameraDevice = camera
                    createCameraSession(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    camera.close()
                    cameraDevice = null
                }
            }, cameraHandler)

        } catch (ex: Exception) {
            Log.e(TAG, "openCamera error", ex)
        }
    }

    private fun createCameraSession(camera: CameraDevice) {
        val surface = imageReader?.surface ?: return
        try {
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            requestBuilder.addTarget(surface)

            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        requestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        try {
                            session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
                            Log.i(TAG, "Camera session configured successfully.")
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "setRepeatingRequest failed", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Camera configuration failed")
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating camera session", e)
        }
    }

    private fun handleImage(image: Image) {
        val nv21 = ImageUtils.imageToNV21(image)
        val width = image.width
        val height = image.height

        val rgbaPixels: IntArray = if (showRaw) {
            // Show raw camera frame
            ImageUtils.nv21ToRgba(nv21, width, height)
        } else {
            // Process with native OpenCV edge detector
            NativeLib.processNV21(nv21, width, height)
        }

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(rgbaPixels, 0, width, 0, 0, width, height)

        lastProcessedBitmap = bmp

        // Update GL texture
        glView.queueEvent {
            renderer.updateBitmap(bmp)
            glView.requestRender()
        }

        // Send Base64 frame to web client
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 60, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        wsClient?.sendFrame(base64)

        // --- FPS Measurement ---
        val now = System.nanoTime()
        frameTimestamps.addLast(now)
        if (frameTimestamps.size > fpsWindow) frameTimestamps.removeFirst()

        if (frameTimestamps.size >= 2) {
            val timeSpan = (frameTimestamps.last() - frameTimestamps.first()) / 1e9
            val fps = (frameTimestamps.size - 1) / timeSpan
            smoothedFps = 0.8 * smoothedFps + 0.2 * fps

            if (now - lastFpsUpdate > 500_000_000) { // update UI every 0.5s
                runOnUiThread {
                    tvFps.text = String.format("FPS: %.1f", smoothedFps)
                }
                lastFpsUpdate = now
            }
        }
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
        stopCameraThread()
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCameraThread()
            openCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wsClient?.close()
    }
}