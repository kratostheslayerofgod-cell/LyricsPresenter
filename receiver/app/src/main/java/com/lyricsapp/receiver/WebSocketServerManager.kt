package com.lyricsapp.receiver

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Manages WebSocket server for receiving commands from the controller.
 * For Phase 1, we use a simple WebSocket client listener.
 * In Phase 2, we'll add a proper WebSocket server.
 */
class WebSocketServerManager(
    private val onMessageReceived: suspend (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit
) : WebSocketListener() {
    
    companion object {
        const val WS_PORT = 8765
        private const val TAG = "WebSocketServer"
    }
    
    private var webSocket: WebSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    
    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
        Log.d(TAG, "WebSocket connected")
        this.webSocket = webSocket
        onConnectionChanged(true)
    }
    
    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d(TAG, "Message received: $text")
        coroutineScope.launch {
            onMessageReceived(text)
        }
    }
    
    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        Log.d(TAG, "Binary message received (not supported yet)")
    }
    
    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "WebSocket closing: $reason")
        webSocket.close(1000, null)
        onConnectionChanged(false)
    }
    
    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "WebSocket closed: $reason")
        onConnectionChanged(false)
    }
    
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
        Log.e(TAG, "WebSocket error", t)
        onConnectionChanged(false)
    }
    
    fun sendMessage(message: String) {
        webSocket?.send(message)
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Closing connection")
        coroutineScope.cancel()
    }
}
