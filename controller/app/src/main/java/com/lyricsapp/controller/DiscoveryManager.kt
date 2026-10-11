package com.lyricsapp.controller

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Discovers receiver apps on the local network via mDNS/NSD and subnet socket scan.
 *
 * Key fixes vs. typical broken setups:
 *  - Holds a MulticastLock (without it Android DROPS the multicast
 *    responses, so the discovered list is always empty).
 *  - SERVICE_TYPE has the REQUIRED trailing dot and is IDENTICAL to
 *    NsdRegistrar.SERVICE_TYPE in the receiver app ("_lyricsapp._tcp.").
 *  - Re-issues discoverServices() every 10s — Android NSD silently
 *    stops delivering results after a while.
 *  - Caps concurrent resolves (max 4).
 *  - Includes parallel subnet scan fallback on port 8765 to bypass router multicast blocks.
 */
class DiscoveryManager(
    private val context: Context,
    private val onReceiversChanged: (List<DiscoveredReceiver>) -> Unit
) {

    data class DiscoveredReceiver(
        val name: String,
        val host: String,
        val port: Int
    )

    companion object {
        private const val TAG = "DiscoveryManager"

        const val SERVICE_TYPE = "_lyricsapp._tcp."
        private const val RESCAN_INTERVAL_MS = 10_000L
        private const val MAX_CONCURRENT_RESOLVES = 4

        fun isEmulator(): Boolean {
            return Build.FINGERPRINT.startsWith("generic") ||
                    Build.FINGERPRINT.startsWith("unknown") ||
                    Build.MODEL.contains("google_sdk") ||
                    Build.MODEL.contains("Emulator") ||
                    Build.MODEL.contains("Android SDK built for x86") ||
                    Build.MANUFACTURER.contains("Genymotion") ||
                    Build.HARDWARE.contains("goldfish") ||
                    Build.HARDWARE.contains("ranchu") ||
                    Build.PRODUCT.contains("sdk_gphone") ||
                    Build.PRODUCT.contains("google_sdk")
        }
    }

    private val nsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val handler = Handler(Looper.getMainLooper())

    /** Android otherwise filters multicast packets used by DNS-SD. */
    private val multicastLock: WifiManager.MulticastLock =
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("lyrics_controller_mdns")
            .apply { setReferenceCounted(false) }

    private val found = LinkedHashMap<String, DiscoveredReceiver>() // name -> receiver
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var activeResolves = 0
    private var started = false
    private var scanJob: Job? = null

    private val rescanRunnable = object : Runnable {
        override fun run() {
            if (!started) return
            Log.d(TAG, "Re-issuing discoverServices (NSD silently dies otherwise)...")
            beginDiscovery()
            handler.postDelayed(this, RESCAN_INTERVAL_MS)
        }
    }

    fun startDiscovery() {
        if (started) {
            // Already running — force a fresh scan
            beginDiscovery()
            return
        }
        started = true
        if (!multicastLock.isHeld) runCatching { multicastLock.acquire() }
        synchronized(found) {
            found.clear()
        }
        beginDiscovery()
        handler.postDelayed(rescanRunnable, RESCAN_INTERVAL_MS)
    }

    fun stopDiscovery() {
        started = false
        scanJob?.cancel()
        scanJob = null
        handler.removeCallbacks(rescanRunnable)
        stopListener()
        if (multicastLock.isHeld) runCatching { multicastLock.release() }
    }

    private fun beginDiscovery() {
        stopListener()
        synchronized(found) {
            found.clear()
            if (isEmulator()) {
                found["emulator_tv"] = DiscoveredReceiver(
                    name = "Android TV Emulator (10.0.2.2)",
                    host = "10.0.2.2",
                    port = 8765
                )
            }
        }
        publish()

        // 1. Trigger subnet scan in parallel
        scanLocalSubnet()

        // 2. Trigger mDNS discovery
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "🔍 Scanning for '$serviceType'")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName
                Log.d(TAG, "📡 Found service: $name — resolving...")
                resolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                synchronized(found) {
                    found.remove(serviceInfo.serviceName)
                }
                publish()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "❌ Start discovery failed (code $errorCode). Will retry on next rescan tick.")
                stopListener()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Stop discovery failed: $errorCode")
            }
        }

        discoveryListener = listener
        runCatching {
            nsdManager.discoverServices(
                SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener
            )
        }.onFailure { Log.e(TAG, "discoverServices threw", it) }
    }

    private fun scanLocalSubnet() {
        scanJob?.cancel()
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            val localIp = getLocalIpAddress()
            if (localIp.isNullOrBlank() || localIp == "Unavailable") return@launch

            val prefix = localIp.substringBeforeLast(".") + "."
            Log.d(TAG, "Starting fast subnet scan on $prefix*")

            val jobs = (1..254).map { lastOctet ->
                async(Dispatchers.IO) {
                    val targetIp = "$prefix$lastOctet"
                    if (targetIp == localIp) return@async
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(targetIp, 8765), 250)
                            Log.d(TAG, "Subnet scan found active receiver at $targetIp:8765")
                            val receiver = DiscoveredReceiver(
                                name = "TV Receiver ($targetIp)",
                                host = targetIp,
                                port = 8765
                            )
                            synchronized(found) {
                                found[targetIp] = receiver
                            }
                            publish()
                        }
                    } catch (_: Exception) {
                        // Timeout / connection refused — no receiver
                    }
                }
            }
            jobs.awaitAll()
            Log.d(TAG, "Subnet scan finished")
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val activeLinkProperties = activeNetwork?.let(connectivityManager::getLinkProperties)
            activeLinkProperties?.linkAddresses
                ?.asSequence()
                ?.mapNotNull { it.address as? Inet4Address }
                ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
                ?.let { return it }

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIpInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (wifiIpInt != 0) {
                return "${wifiIpInt and 0xff}.${wifiIpInt shr 8 and 0xff}.${wifiIpInt shr 16 and 0xff}.${wifiIpInt shr 24 and 0xff}"
            }

            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback) continue
                val addresses = networkInterface.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (!ip.startsWith("127.") && ip != "0.0.0.0") {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
        }
        return null
    }

    private fun resolve(info: NsdServiceInfo) {
        if (activeResolves >= MAX_CONCURRENT_RESOLVES) {
            Log.w(TAG, "Too many concurrent resolves, skipping ${info.serviceName}")
            return
        }
        activeResolves++

        val listener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                activeResolves--
                var host = resolved.host?.hostAddress
                if (host.isNullOrBlank()) {
                    Log.w(TAG, "Resolved ${resolved.serviceName} but host was null")
                    return
                }
                if (host.startsWith("/")) {
                    host = host.substring(1)
                }
                if (host == "10.0.2.15" && isEmulator()) {
                    host = "10.0.2.2"
                }

                val displayName = resolved.serviceName
                    .removeSuffix(SERVICE_TYPE)
                    .removeSuffix(".")
                    .ifEmpty { "Lyrics Display" }

                Log.i(TAG, "✅ Resolved: '$displayName' @ $host:${resolved.port}")
                synchronized(found) {
                    found[resolved.serviceName] = DiscoveredReceiver(
                        name = displayName,
                        host = host,
                        port = resolved.port
                    )
                }
                publish()
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                activeResolves--
                Log.w(TAG, "Resolve failed for '${serviceInfo.serviceName}' (code $errorCode)")
            }
        }

        runCatching { nsdManager.resolveService(info, listener) }
            .onFailure {
                activeResolves--
                Log.e(TAG, "resolveService threw for ${info.serviceName}", it)
            }
    }

    private fun stopListener() {
        discoveryListener?.let { listener ->
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
    }

    private fun publish() {
        val list = synchronized(found) {
            found.values.toList()
        }
        handler.post {
            onReceiversChanged(list)
        }
    }
}
