package com.lyricsapp.controller

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Service-owned WebSocket client. Callback and parsing failures are contained on OkHttp threads. */
class WebSocketClientManager(
    context: Context,
    private val onMessageReceived: suspend (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit,
    private val onRepeatedDisconnects: () -> Unit = {}
) : WebSocketListener() {

    companion object {
        private const val TAG = "PresentationService"
        private const val WS_PORT = 8765
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
        private const val MAX_RETRY_MS = 16_000L
        private const val PREFS = "presentation_connection"
    }

    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val exceptionHandler = CoroutineExceptionHandler { _, error -> Log.e(TAG, "Unhandled connection coroutine failure", error) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var isConnected = false
    @Volatile private var isConnecting = false
    @Volatile private var currentHost: String? = preferences.getString("host", null)
    @Volatile private var isUserDisconnect = false
    @Volatile private var lossHandled = false
    @Volatile private var missedHeartbeats = 0
    @Volatile private var connectionDropCount = 0
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var retryDelayMs = 1_000L

    fun connect(receiverHost: String) {
        val rawHost = receiverHost.trim().removePrefix("ws://").removePrefix("http://").removePrefix("/").substringBefore('/')
        val cleanHost = when {
            rawHost.startsWith("[") -> rawHost.substringAfter('[').substringBefore(']')
            rawHost.count { it == ':' } == 1 -> rawHost.substringBefore(':')
            else -> rawHost
        }.trim()
        if (cleanHost.isBlank()) {
            safeConnectionCallback(false)
            return
        }
        isUserDisconnect = false
        if (currentHost != null && currentHost != cleanHost && (isConnected || isConnecting)) {
            val previousSocket = webSocket
            webSocket = null
            isConnected = false
            isConnecting = false
            lossHandled = true
            heartbeatJob?.cancel()
            runCatching { previousSocket?.cancel() }
        }
        currentHost = cleanHost
        preferences.edit().putString("host", cleanHost).apply()
        retryDelayMs = 1_000L
        reconnectJob?.cancel()
        connectInternal(cleanHost)
    }

    private fun connectInternal(host: String) {
        synchronized(this) {
            if (isConnected || isConnecting || isUserDisconnect) return
            isConnecting = true
            lossHandled = false
        }
        try {
            webSocket?.close(1000, "Reconnecting")
            val urlHost = if (host.contains(':')) "[${host.replace("%", "%25")}]" else host
            webSocket = httpClient.newWebSocket(Request.Builder().url("ws://$urlHost:$WS_PORT").build(), this)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to create WebSocket for $host", error)
            isConnecting = false
            handleConnectionLoss()
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        try {
            this.webSocket = webSocket
            isConnecting = false
            isConnected = true
            lossHandled = false
            missedHeartbeats = 0
            retryDelayMs = 1_000L
            reconnectJob?.cancel()
            safeConnectionCallback(true)
            check(webSocket.send("""{"type":"HANDSHAKE","protocolVersion":2,"timestamp":${System.currentTimeMillis()}}"""))
            startHeartbeat()
        } catch (error: Exception) {
            Log.e(TAG, "WebSocket open callback failed", error)
            handleConnectionLoss()
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            if (text.length > 2_000_000) {
                Log.w(TAG, "Dropping oversized WebSocket message (${text.length} chars)")
                return
            }
            runCatching { JSONObject(text) }.onFailure { Log.w(TAG, "Ignoring malformed receiver JSON", it); return }
            if (runCatching { JSONObject(text).optString("type") }.getOrNull() == "HEARTBEAT_ACK") missedHeartbeats = 0
            scope.launch {
                try {
                    onMessageReceived(text)
                } catch (error: Exception) {
                    Log.e(TAG, "Receiver message handler failed", error)
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "WebSocket message callback failed", error)
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        try { Log.w(TAG, "Ignoring unsupported binary frame (${bytes.size} bytes)") }
        catch (error: Exception) { Log.e(TAG, "Binary callback failed", error) }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        try {
            if (this.webSocket !== webSocket) return
            webSocket.close(1000, "Closing")
            handleConnectionLoss()
        }
        catch (error: Exception) { Log.e(TAG, "WebSocket closing callback failed", error); handleConnectionLoss() }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        try { if (this.webSocket === webSocket) handleConnectionLoss() }
        catch (error: Exception) { Log.e(TAG, "WebSocket closed callback failed", error) }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        try {
            if (this.webSocket !== webSocket) return
            Log.e(TAG, "WebSocket failure (${response?.code}): ${t.message}", t)
            isConnecting = false
            handleConnectionLoss()
        }
        catch (error: Exception) { Log.e(TAG, "WebSocket failure callback failed", error) }
    }

    fun sendMessage(message: String) {
        try {
            val type = runCatching { JSONObject(message).optString("type") }.getOrDefault("")
            recordRecoveryState(type, message)
            val socket = webSocket
            if (!isConnected || socket == null || !socket.send(message)) {
                Log.w(TAG, "Send failed or socket is unavailable (type=$type)")
                if (isConnected) handleConnectionLoss()
            }
        } catch (error: Exception) {
            Log.e(TAG, "Outgoing message failed", error)
            handleConnectionLoss()
        }
    }

    /** Called after the receiver's blank-on-connect HANDSHAKE ACK. */
    fun restoreLastState() {
        try {
            if (!isConnected) return
            val background = preferences.getString("background", null)
            val display = preferences.getString("display", null)
            val style = preferences.getString("style", null)
            val clearAll = preferences.getString("clearAll", null)
            val lyricsClear = preferences.getString("lyricsClear", null)
            val blackOverride = preferences.getString("blackOverride", null)
            val overlayClear = preferences.getString("overlayClear", null)
            background?.let { sendRaw(it) }
            display?.let { sendRaw(it) }
            style?.let { sendRaw(it) }
            clearAll?.let { sendRaw(it) }
            lyricsClear?.let { sendRaw(it) }
            blackOverride?.let { sendRaw(it) }
            overlayClear?.let { sendRaw(it) }
            Log.i(TAG, "Restored saved presentation state after handshake")
        } catch (error: Exception) {
            Log.e(TAG, "Unable to restore presentation state", error)
        }
    }

    private fun recordRecoveryState(type: String, message: String) {
        when (type) {
            "DISPLAY" -> preferences.edit()
                .putString("display", message)
                .remove("style")
                .remove("clearAll")
                .remove("lyricsClear")
                .remove("blackOverride")
                .remove("overlayClear")
                .apply()
            "SET_BACKGROUND" -> {
                val edit = preferences.edit().putString("background", message)
                if (preferences.contains("clearAll")) {
                    edit.remove("clearAll")
                        .putString("lyricsClear", JSONObject().put("type", "CLEAR_LYRICS").put("timestamp", System.currentTimeMillis()).toString())
                }
                edit.apply()
            }
            "CLEAR_BACKGROUND" -> preferences.edit().remove("background").apply()
            "NEXT_SLIDE", "PREV_SLIDE", "JUMP_SLIDE" -> {
                runCatching {
                    val saved = preferences.getString("display", null)
                    if (saved != null) {
                        val state = JSONObject(saved)
                        val slides = state.optJSONArray("slides")
                        val current = state.optInt("currentSlide", 0)
                        val requested = JSONObject(message).optInt("slideIndex", current)
                        val validJump = type != "JUMP_SLIDE" || (slides != null && requested in 0 until slides.length())
                        if (validJump && slides != null && slides.length() > 0) {
                            val next = when (type) {
                                "NEXT_SLIDE" -> (current + 1).coerceAtMost(slides.length() - 1)
                                "PREV_SLIDE" -> (current - 1).coerceAtLeast(0)
                                else -> requested
                            }
                            state.put("currentSlide", next)
                            preferences.edit().putString("display", state.toString()).apply()
                        }
                        if (validJump) {
                            preferences.edit().remove("clearAll").remove("lyricsClear").remove("blackOverride").apply()
                        }
                    }
                }.onFailure { Log.w(TAG, "Could not update persisted slide position", it) }
            }
            "BLACK_SCREEN" -> preferences.edit().putString("blackOverride", message).apply()
            "CLEAR_SCREEN" -> preferences.edit().remove("clearAll").remove("blackOverride").putString("lyricsClear", JSONObject().put("type", "CLEAR_LYRICS").put("timestamp", System.currentTimeMillis()).toString()).apply()
            "CLEAR_LYRICS" -> preferences.edit().putString("lyricsClear", message).apply()
            "CLEAR_OVERLAY" -> preferences.edit().putString("overlayClear", message).apply()
            "CLEAR_ALL" -> preferences.edit()
                .remove("background")
                .remove("lyricsClear")
                .remove("blackOverride")
                .remove("overlayClear")
                .putString("clearAll", message)
                .apply()
            "APPLY_TEMPLATE", "UPDATE_STYLE" -> preferences.edit().putString("style", message).remove("overlayClear").apply()
        }
    }

    private fun sendRaw(message: String) {
        try { webSocket?.send(message) } catch (error: Exception) { Log.e(TAG, "State restore send failed", error) }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isConnected) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (!isConnected) break
                missedHeartbeats++
                if (missedHeartbeats >= 2) {
                    Log.w(TAG, "Two heartbeat acknowledgements missed; reconnecting")
                    webSocket?.cancel()
                    handleConnectionLoss()
                    break
                }
                sendMessage("""{"type":"HEARTBEAT","timestamp":${System.currentTimeMillis()}}""")
            }
        }
    }

    @Synchronized
    private fun handleConnectionLoss() {
        if (lossHandled && !isConnected && !isConnecting) return
        lossHandled = true
        val wasConnected = isConnected
        isConnected = false
        isConnecting = false
        heartbeatJob?.cancel()
        safeConnectionCallback(false)
        if (wasConnected) {
            connectionDropCount++
            if (connectionDropCount == 3) runCatching(onRepeatedDisconnects).onFailure { Log.e(TAG, "Disconnect hint callback failed", it) }
        }
        if (!isUserDisconnect && currentHost != null) scheduleAutoReconnect()
    }

    private fun scheduleAutoReconnect() {
        if (isUserDisconnect || currentHost == null || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            while (isActive && !isConnected && !isUserDisconnect) {
                val delayMs = retryDelayMs
                Log.i(TAG, "Reconnect in ${delayMs}ms")
                delay(delayMs)
                if (!isConnected && !isUserDisconnect) currentHost?.let(::connectInternal)
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
            }
        }
    }

    private fun safeConnectionCallback(connected: Boolean) {
        try { onConnectionChanged(connected) }
        catch (error: Exception) { Log.e(TAG, "Connection callback failed", error) }
    }

    fun disconnect() {
        isUserDisconnect = true
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        isConnecting = false
        isConnected = false
        runCatching { webSocket?.close(1000, "User disconnected") }
            .onFailure { Log.w(TAG, "Socket close failed", it) }
        webSocket = null
        currentHost = null
        preferences.edit().remove("host").apply()
        lossHandled = true
        safeConnectionCallback(false)
    }

    fun restoreSavedHost() {
        currentHost?.takeIf { !isConnected && !isConnecting }?.let { connect(it) }
    }

    fun isConnectedNow(): Boolean = isConnected

    fun close() {
        disconnect()
        scope.cancel()
        httpClient.dispatcher.cancelAll()
        httpClient.connectionPool.evictAll()
    }
}
