package com.lyricsapp.controller

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns the presentation socket independently from any Activity instance. */
class ConnectionKeepAliveService : Service() {

    companion object {
        private const val TAG = "PresentationService"
        private const val CHANNEL_ID = "presentation_service"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TIMEOUT_MS = 10L * 60L * 60L * 1000L
        private const val EXTRA_RESTORE = "restore_saved_connection"

        fun start(context: Context, restoreSavedConnection: Boolean = true) {
            val intent = Intent(context, ConnectionKeepAliveService::class.java)
                .putExtra(EXTRA_RESTORE, restoreSavedConnection)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionKeepAliveService::class.java))
        }
    }

    inner class LocalBinder : Binder() {
        fun service(): ConnectionKeepAliveService = this@ConnectionKeepAliveService
    }

    private val binder = LocalBinder()
    private val _connectionState = MutableStateFlow(false)
    val connectionState = _connectionState.asStateFlow()
    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingMessages = _incomingMessages.asSharedFlow()
    private val _repeatedDisconnects = MutableStateFlow(0)
    val repeatedDisconnects = _repeatedDisconnects.asStateFlow()

    lateinit var webSocketClient: WebSocketClientManager
        private set
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        try {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:PresentationSocket")
                .apply { setReferenceCounted(false); acquire(WAKE_LOCK_TIMEOUT_MS) }
        } catch (error: Exception) {
            Log.w(TAG, "Could not acquire partial wake lock", error)
        }
        webSocketClient = WebSocketClientManager(
            context = applicationContext,
            onMessageReceived = { raw ->
                try {
                    val message = org.json.JSONObject(raw)
                    if (message.optString("type") == "ACK" && message.optString("receivedMessageType") == "HANDSHAKE" && message.optBoolean("success")) {
                        webSocketClient.restoreLastState()
                    }
                    _incomingMessages.emit(raw)
                } catch (error: Exception) {
                    Log.w(TAG, "Ignoring invalid receiver message", error)
                }
            },
            onConnectionChanged = { connected -> _connectionState.value = connected },
            onRepeatedDisconnects = { _repeatedDisconnects.value = _repeatedDisconnects.value + 1 }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.getBooleanExtra(EXTRA_RESTORE, false)) webSocketClient.restoreSavedHost()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        try { webSocketClient.close() } catch (error: Exception) { Log.w(TAG, "Socket shutdown failed", error) }
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (error: Exception) { Log.w(TAG, "Wake lock release failed", error) }
        wakeLock = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Presentation Service", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the live presentation connection active"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Presentation Service")
        .setContentText("Keeping the receiver connection available")
        .setSmallIcon(R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()
}
