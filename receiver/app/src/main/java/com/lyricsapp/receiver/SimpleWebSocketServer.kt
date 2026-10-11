package com.lyricsapp.receiver

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.java_websocket.WebSocket
import org.java_websocket.framing.CloseFrame
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

/**
 * Stable WebSocket server for the Receiver app.
 *
 * Drop-in replacement for the old hand-rolled socket server. Same class name,
 * same constructor signature, same start()/stop() calls — MainActivity needs
 * no changes. Built on the battle-tested Java-WebSocket library:
 *
 *  - Correct frame parsing (masking, fragmentation, 16/64-bit lengths)
 *  - Proper ping/pong + close handshake (interoperates with OkHttp client)
 *  - Dead-peer detection via connectionLostTimeout (pings, expects pongs)
 *  - Auto-restart loop if the server ever crashes
 *  - Single active controller at a time (new connection takes over cleanly)
 *
 * REQUIRED dependency in receiver/app/build.gradle.kts:
 *     implementation("org.java-websocket:Java-WebSocket:1.5.6")
 *
 * (It pulls in slf4j-api with no binding — the one-time console warning is
 * harmless, or add: implementation("org.slf4j:slf4j-nop:2.0.12"))
 */
class SimpleWebSocketServer(
    private val port: Int = 8765,
    private val onMessageReceived: suspend (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit
) {

    companion object {
        private const val TAG = "WebSocketServer"

        /** Seconds without a pong before a client is declared dead. */
        private const val CONNECTION_LOST_TIMEOUT_SEC = 45
    }

    private var server: InnerServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var active: WebSocket? = null

    @Volatile
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            while (isActive) {                    // auto-restart if server ever dies
                try {
                    val s = InnerServer()
                    server = s
                    s.start()
                    Log.d(TAG, "WebSocket server started successfully on port $port")
                    awaitCancellation()   // suspends until cancelled/stopped
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Server crashed; restarting in 3s", e)
                }
                if (isActive) delay(3_000)
            }
        }
    }

    fun stop() {
        isRunning = false
        runCatching { server?.stop(1_000) }
        server = null
        active = null
        scope.cancel()
    }

    /**
     * Send a text message to the connected controller (e.g. ACK / HEARTBEAT_ACK).
     * Returns false if no controller is connected (message is dropped, no crash).
     */
    fun sendToController(text: String): Boolean {
        val conn = active
        return if (conn != null && conn.isOpen) {
            conn.send(text)
            true
        } else {
            Log.w(TAG, "sendToController dropped (no controller connected)")
            false
        }
    }

    private inner class InnerServer : WebSocketServer(InetSocketAddress(port)) {

        init {
            setReuseAddr(true)
        }

        override fun onStart() {
            Log.d(TAG, "Server listening on port $port")
            setConnectionLostTimeout(CONNECTION_LOST_TIMEOUT_SEC)
        }

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            Log.d(TAG, "Client handshake complete. Connected to Controller! (${conn.remoteSocketAddress})")

            // Single-operator design: a new controller takes over from the old one.
            // Swap [active] FIRST so the old socket's onClose() does not fire
            // a spurious "Disconnected" event to the UI.
            val previous = active
            active = conn
            previous?.takeIf { it.isOpen && it != conn }
                ?.close(CloseFrame.NORMAL, "New controller took over")

            onConnectionChanged(true)
        }

        override fun onMessage(conn: WebSocket, message: String) {
            Log.d(TAG, "Received frame payload (${message.length} chars)")
            scope.launch {
                try {
                    onMessageReceived(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in message handler", e)
                }
            }
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            Log.d(TAG, "Client disconnected: code=$code reason='$reason' remote=$remote")
            if (active === conn) {
                active = null
                onConnectionChanged(false)
            }
        }

        override fun onError(conn: WebSocket?, ex: Exception) {
            // Also receives benign "connection reset" noise when a client
            // vanishes mid-frame; onClose() handles the state cleanup.
            Log.e(TAG, "WebSocket error (${conn?.remoteSocketAddress})", ex)
        }
    }
}