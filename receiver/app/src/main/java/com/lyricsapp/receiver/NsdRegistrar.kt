package com.lyricsapp.receiver

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

/**
 * Registers the receiver as an mDNS/NSD service so controllers can find it.
 *
 * Key fixes for cross-device discovery:
 *  - Holds a MulticastLock (Android drops multicast packets without it).
 *  - Service type has the REQUIRED trailing dot ("_lyricsapp._tcp.").
 *  - Service label is DNS-SD compliant (<= 15 chars incl. underscore).
 *  - Auto-retries if registration fails.
 */
class NsdRegistrar(private val context: Context) {

    companion object {
        private const val TAG = "NsdRegistrar"

        // Trailing dot is REQUIRED by Android NSD.
        // Keep it SHORT and IDENTICAL to DiscoveryManager.SERVICE_TYPE
        // in the controller app.
        const val SERVICE_TYPE = "_lyricsapp._tcp."
        const val DEFAULT_NAME = "Lyrics Display"
        private const val RETRY_MS = 5_000L

        fun getDeviceName(context: Context): String {
            val globalName = runCatching {
                Settings.Global.getString(context.contentResolver, "device_name")
            }.getOrNull()
            if (!globalName.isNullOrBlank()) return globalName

            val btName = runCatching {
                Settings.Secure.getString(context.contentResolver, "bluetooth_name")
            }.getOrNull()
            if (!btName.isNullOrBlank()) return btName

            return Build.MODEL
        }
    }

    private val nsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val handler = Handler(Looper.getMainLooper())

    /**
     * CRITICAL: without this lock the radio dozes and mDNS announcements
     * never leave the device. Released in unregisterService().
     */
    private val multicastLock: WifiManager.MulticastLock =
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("lyrics_receiver_mdns")
            .apply {
                setReferenceCounted(true)
                acquire()
            }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var lastPort: Int = 8765
    private var lastName: String? = null

    private val retryRunnable = Runnable {
        registerService(lastPort, lastName)
    }

    fun registerService(port: Int = 8765, customServiceName: String? = null) {
        lastPort = port
        lastName = customServiceName
        unregisterListenersOnly()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = customServiceName?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("mediaPort", "8766")
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "✅ Service registered: '${info.serviceName}' " +
                        "type=${info.serviceType} port=${info.port}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "❌ Registration failed (code $errorCode), retrying in ${RETRY_MS}ms")
                handler.postDelayed(retryRunnable, RETRY_MS)
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "Service unregistered: ${info.serviceName}")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Unregistration failed: $errorCode")
            }
        }

        Log.d(TAG, "Registering '$SERVICE_TYPE' on port $port...")
        nsdManager.registerService(
            serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener
        )
    }

    fun unregisterService() {
        handler.removeCallbacks(retryRunnable)
        unregisterListenersOnly()
        if (multicastLock.isHeld) runCatching { multicastLock.release() }
    }

    private fun unregisterListenersOnly() {
        registrationListener?.let { listener ->
            runCatching { nsdManager.unregisterService(listener) }
        }
        registrationListener = null
    }
}
