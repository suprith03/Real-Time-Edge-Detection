package com.suprith.realtimeedgedetectionviewer

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class FrameWebSocketClient(serverUri: URI) : WebSocketClient(serverUri) {

    private val TAG = "FrameWebSocketClient"

    override fun onOpen(handshakedata: ServerHandshake?) {
        Log.i(TAG, "✅ Connected to relay server")
        send("ANDROID_HELLO") // Identify as Android device
    }

    override fun onMessage(message: String?) {
        Log.d(TAG, "Message from server: $message")
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        Log.w(TAG, "Connection closed: $reason")
    }

    override fun onError(ex: Exception?) {
        Log.e(TAG, "WebSocket error", ex)
    }

    fun sendFrame(base64Image: String) {
        if (isOpen) send(base64Image)
    }
}
