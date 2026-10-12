package com.lyricsapp.receiver

import android.graphics.Typeface
import android.os.Bundle
import android.net.wifi.WifiManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.lyricsapp.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {
    
    private lateinit var wsServer: SimpleWebSocketServer
    private lateinit var nsdRegistrar: NsdRegistrar
    private lateinit var mediaServer: EmbeddedMediaServer
    private lateinit var displayPreferences: DisplayPreferences
    private var wifiLock: WifiManager.WifiLock? = null
    
    private val slides = mutableStateListOf<String>()
    private var currentSlideIndex by mutableStateOf(0)
    private var songTitle by mutableStateOf("")
    private var displayText by mutableStateOf("Waiting for connection...")
    private var isConnected by mutableStateOf(false)
    private var isBlackScreen by mutableStateOf(false)
    private var isBlank by mutableStateOf(true)
    private var lyricsVisible by mutableStateOf(false)
    private var backgroundVisible by mutableStateOf(false)
    private var backgroundPlayerRetained by mutableStateOf(false)
    private var overlayDim by mutableStateOf(0)
    private var mediaLoop by mutableStateOf(true)
    private var mediaMuted by mutableStateOf(true)
    private var mediaFit by mutableStateOf("FILL")
    private var mediaPlaying by mutableStateOf(true)
    private var mediaRestartKey by mutableIntStateOf(0)
    private var textBold by mutableStateOf(false)
    private var textItalic by mutableStateOf(false)
    private var horizontalAlignment by mutableStateOf("center")
    private var verticalAlignment by mutableStateOf("center")
    private var lineSpacing by mutableFloatStateOf(1.25f)
    private var shadowColor by mutableStateOf<String?>(null)
    private var textPaddingDp by mutableIntStateOf(40)
    private var ipAddress by mutableStateOf("")
    private var deviceName by mutableStateOf("")
    
    // Display Settings State
    private var fontFamily by mutableStateOf("SansSerif")
    private var fontSize by mutableStateOf(48)
    private var textColor by mutableStateOf("#00E5FF")
    private var backgroundType by mutableStateOf("COLOR")
    private var backgroundValue by mutableStateOf("#1E3A8A")
    private var backgroundColorHex by mutableStateOf("#1E3A8A")
    private var backgroundGradientEndColor by mutableStateOf("#FF1E3A8A")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersive4kMode()
        
        nsdRegistrar = NsdRegistrar(applicationContext)
        displayPreferences = DisplayPreferences(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            val saved = displayPreferences.restore()
            launch(Dispatchers.Main) { textColor = saved.first; backgroundValue = saved.second; backgroundColorHex = saved.second }
        }
        deviceName = NsdRegistrar.getDeviceName(applicationContext)
        ipAddress = getLocalIpAddress()
        mediaServer = EmbeddedMediaServer(cacheDir, mediaUrlFor = ::resolveMediaUrl, onReady = { mediaId, type, url ->
            wsServer.sendToController(JSONObject().put("type", "MEDIA_READY").put("mediaId", mediaId).put("url", url).put("mediaType", type).put("timestamp", System.currentTimeMillis()).toString())
        }, onError = { reason -> wsServer.sendToController(JSONObject().put("type", "MEDIA_ERROR").put("reason", reason).put("timestamp", System.currentTimeMillis()).toString()) })
        mediaServer.start()
        
        wsServer = SimpleWebSocketServer(
            port = 8765,
            onMessageReceived = { message ->
                val type = runCatching { JSONObject(message).optString("type") }.getOrDefault("")
                if (type == "HEARTBEAT") {
                    wsServer.sendToController("""{"type":"HEARTBEAT_ACK","timestamp":${System.currentTimeMillis()}}""")
                } else {
                    lifecycleScope.launch(Dispatchers.Main) { handleIncomingMessage(message) }
                }
            },
            onConnectionChanged = { connected ->
                lifecycleScope.launch(Dispatchers.Main) {
                    isConnected = connected
                    if (connected) acquireWifiLock() else releaseWifiLock()
                    if (!connected) {
                        displayText = "Connection lost. Waiting for controller..."
                        ipAddress = getLocalIpAddress()
                    } else {
                        // A new session always starts black. The controller may restore its
                        // latest DISPLAY state after reconnect; until then expose no stale UI.
                        Log.i("BlankState", "Controller connected; entering blank output")
                        isBlank = true
                        isBlackScreen = false
                        lyricsVisible = false
                        backgroundPlayerRetained = backgroundVisible && backgroundType == "VIDEO"
                        backgroundVisible = false
                        overlayDim = 0
                        displayText = ""
                    }
                }
            }
        )
        
        wsServer.start()
        nsdRegistrar.registerService(port = 8765, customServiceName = deviceName)
        ServerKeepAliveService.start(applicationContext)
        
        setContent {
            LyricsReceiverTheme {
            ReceiverScreen(
                deviceName = deviceName,
                displayText = displayText,
                isConnected = isConnected,
                isBlackScreen = isBlackScreen,
                isBlank = isBlank,
                ipAddress = ipAddress,
                port = 8765,
                fontFamilyStr = fontFamily,
                fontSize = fontSize,
                textColorHex = textColor,
                backgroundType = backgroundType,
                backgroundValue = backgroundValue,
                backgroundColorHex = backgroundColorHex,
                backgroundGradientEndColor = backgroundGradientEndColor,
                backgroundVisible = backgroundVisible,
                backgroundPlayerRetained = backgroundPlayerRetained,
                lyricsVisible = lyricsVisible,
                overlayDim = overlayDim,
                mediaLoop = mediaLoop,
                mediaMuted = mediaMuted,
                mediaFit = mediaFit,
                mediaPlaying = mediaPlaying,
                mediaRestartKey = mediaRestartKey,
                textBold = textBold,
                textItalic = textItalic,
                horizontalAlignment = horizontalAlignment,
                verticalAlignment = verticalAlignment,
                lineSpacing = lineSpacing
                , shadowColorHex = shadowColor,
                textPaddingDp = textPaddingDp
            )
            }
        }
    }
    
    private fun handleIncomingMessage(rawJson: String) {
        try {
            val json = JSONObject(rawJson)
            val type = json.optString("type", "")
            
            when (type) {
                "DISPLAY" -> {
                    val title = json.optString("songTitle", "Lyrics Display")
                    val slideIndex = json.optInt("currentSlide", 0)
                    val slidesArray = json.optJSONArray("slides")
                    val list = mutableListOf<String>()
                    if (slidesArray != null) {
                        for (i in 0 until slidesArray.length()) {
                            val slideObj = slidesArray.getJSONObject(i)
                            list.add(slideObj.optString("content", ""))
                        }
                    }
                    
                    val settings = json.optJSONObject("displaySettings")
                    if (settings != null) {
                        fontFamily = settings.optString("fontFamily", "SansSerif")
                        fontSize = settings.optInt("fontSize", 48)
                        textColor = settings.optString("textColor", "#FFFFFF")
                        // DISPLAY updates lyrics/style only. Background media is an independent
                        // layer and can be changed only by SET_BACKGROUND / CLEAR_BACKGROUND.
                        val mediaUrl = settings.optString("backgroundMediaUrl", "")
                        if (settings.has("backgroundMediaUrl") && (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://"))) {
                            backgroundValue = mediaUrl
                            backgroundType = settings.optString("backgroundType", "IMAGE").uppercase()
                                .let { if (it !in setOf("IMAGE", "VIDEO")) "IMAGE" else it }
                            backgroundVisible = true
                        } else if (!backgroundVisible) {
                            backgroundType = settings.optString("backgroundType", "COLOR").uppercase()
                            backgroundValue = settings.optString("backgroundValue", backgroundColorHex)
                            backgroundGradientEndColor = settings.optString("backgroundGradientEndColor", backgroundGradientEndColor)
                            if (backgroundType == "COLOR" || backgroundType == "SOLID") backgroundColorHex = backgroundValue
                        }
                        overlayDim = settings.optInt("overlayDimPercent", 0).coerceIn(0, 100)
                        mediaFit = settings.optString("mediaFitMode", "FILL")
                        textBold = settings.optBoolean("textBold", false)
                        textItalic = settings.optBoolean("textItalic", false)
                        horizontalAlignment = settings.optString("horizontalAlignment", "center")
                        verticalAlignment = settings.optString("verticalAlignment", "center")
                        lineSpacing = settings.optDouble("lineSpacing", 1.25).toFloat().coerceIn(0.8f, 2.5f)
                        shadowColor = settings.optString("shadowColor", "").takeIf { it.isNotBlank() }
                        textPaddingDp = settings.optInt("textPaddingDp", textPaddingDp).coerceIn(0, 200)
                        lifecycleScope.launch(Dispatchers.IO) { displayPreferences.save(textColor, backgroundColorHex) }
                    }

                    slides.clear()
                    slides.addAll(list)
                    songTitle = title
                    currentSlideIndex = slideIndex.coerceIn(0, maxOf(0, list.size - 1))
                    displayText = list.getOrNull(currentSlideIndex).orEmpty()
                    isBlackScreen = false
                    isBlank = list.isEmpty() && !backgroundVisible
                    lyricsVisible = list.isNotEmpty()
                }
                "NEXT_SLIDE" -> {
                    if (slides.isNotEmpty() && currentSlideIndex < slides.size - 1) {
                        currentSlideIndex++
                    }
                    isBlackScreen = false
                    isBlank = slides.isEmpty() && !backgroundVisible
                    lyricsVisible = slides.isNotEmpty()
                    displayText = slides.getOrNull(currentSlideIndex).orEmpty()
                }
                "PREV_SLIDE" -> {
                    if (slides.isNotEmpty() && currentSlideIndex > 0) {
                        currentSlideIndex--
                    }
                    isBlackScreen = false
                    isBlank = slides.isEmpty() && !backgroundVisible
                    lyricsVisible = slides.isNotEmpty()
                    displayText = slides.getOrNull(currentSlideIndex).orEmpty()
                }
                "JUMP_SLIDE" -> {
                    val index = json.optInt("slideIndex", -1)
                    slides.getOrNull(index)?.let { slide ->
                        currentSlideIndex = index
                        displayText = slide
                        isBlank = false
                        isBlackScreen = false
                        lyricsVisible = true
                    }
                }
                "BLACK_SCREEN" -> {
                    isBlackScreen = true
                    isBlank = false
                    lyricsVisible = false
                }
                "CLEAR_SCREEN" -> {
                    isBlackScreen = false
                    isBlank = false
                    lyricsVisible = false
                    displayText = ""
                }
                "SET_BACKGROUND" -> {
                    val mediaId = json.optString("mediaId", "")
                    val requestedUrl = json.optString("mediaUrl").takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    val mediaUrl = requestedUrl?.takeIf { !it.contains("localhost") && !it.contains("127.0.0.1") } ?: resolveMediaUrl(mediaId)
                    if (mediaId.isNotBlank() && !mediaUrl.isNullOrBlank()) {
                        backgroundValue = mediaUrl
                        backgroundType = json.optString("mediaType", json.optString("type", "IMAGE")).uppercase().let { if (it == "SET_BACKGROUND") "IMAGE" else it }
                        mediaFit = json.optString("fit", "FILL").uppercase()
                        mediaLoop = json.optBoolean("loop", true)
                        mediaMuted = json.optBoolean("muted", true)
                        backgroundVisible = true
                        backgroundPlayerRetained = backgroundType == "VIDEO"
                        Log.i("BackgroundLayer", "Background set: type=$backgroundType fit=$mediaFit")
                        isBlank = false
                    } else {
                        wsServer.sendToController(JSONObject().put("type", "MEDIA_ERROR").put("mediaId", mediaId).put("reason", "Receiver media address is unavailable").put("timestamp", System.currentTimeMillis()).toString())
                    }
                }
                "CLEAR_BACKGROUND" -> { backgroundVisible = false; backgroundPlayerRetained = false; isBlank = !lyricsVisible; Log.i("BackgroundLayer", "Background cleared") }
                "CLEAR_LYRICS" -> { lyricsVisible = false; displayText = ""; isBlank = !backgroundVisible }
                "CLEAR_OVERLAY" -> overlayDim = 0
                "CLEAR_ALL" -> { backgroundVisible = false; backgroundPlayerRetained = false; lyricsVisible = false; overlayDim = 0; displayText = ""; isBlackScreen = false; isBlank = true; Log.i("BackgroundLayer", "All output layers cleared") }
                "VIDEO_PLAY" -> mediaPlaying = true
                "VIDEO_PAUSE" -> mediaPlaying = false
                "VIDEO_RESTART" -> { mediaRestartKey++; mediaPlaying = true }
                "VIDEO_LOOP" -> mediaLoop = json.optBoolean("enabled", true)
                "VIDEO_MUTE" -> mediaMuted = json.optBoolean("enabled", true)
                "UPDATE_STYLE" -> {
                    if (json.has("textColor")) textColor = json.optString("textColor", textColor)
                    if (json.has("bgColor")) {
                        backgroundColorHex = json.optString("bgColor", backgroundColorHex)
                        if (!backgroundVisible) { backgroundType = "COLOR"; backgroundValue = backgroundColorHex }
                    }
                    if (json.has("backgroundAlpha")) overlayDim = json.optInt("backgroundAlpha", 0).coerceIn(0, 100)
                    if (json.has("fontSize")) fontSize = json.optInt("fontSize", fontSize).coerceIn(16, 160)
                    lifecycleScope.launch(Dispatchers.IO) { displayPreferences.save(textColor, backgroundColorHex) }
                }
                "APPLY_TEMPLATE" -> json.optJSONObject("template")?.let { template ->
                    fontFamily = template.optString("fontFamily", fontFamily)
                    fontSize = template.optInt("fontSize", fontSize)
                    textColor = template.optString("textColor", textColor)
                    backgroundColorHex = template.optString("backgroundColor", backgroundColorHex)
                    backgroundGradientEndColor = template.optString("backgroundGradientEndColor", backgroundGradientEndColor)
                    val templateBackgroundType = template.optString("backgroundType", backgroundType).uppercase()
                    if (!backgroundVisible) {
                        backgroundType = if (templateBackgroundType == "SOLID") "COLOR" else templateBackgroundType
                        backgroundValue = backgroundColorHex
                    }
                    mediaFit = template.optString("mediaFitMode", mediaFit).uppercase()
                    textBold = template.optBoolean("bold", false)
                    textItalic = template.optBoolean("italic", false)
                    horizontalAlignment = template.optString("horizontalAlignment", "center")
                    verticalAlignment = template.optString("verticalAlignment", "center")
                    lineSpacing = template.optDouble("lineSpacing", 1.25).toFloat().coerceIn(0.8f, 2.5f)
                    shadowColor = template.optString("shadowColor", "").takeIf { it.isNotBlank() }
                    textPaddingDp = template.optInt("paddingDp", 40).coerceIn(0, 200)
                    val mediaId = template.optString("backgroundMediaId", "")
                    val templateMediaUrl = template.optString("backgroundMediaUrl", "")
                    if (mediaId.isNotBlank() && (templateMediaUrl.startsWith("http://") || templateMediaUrl.startsWith("https://"))) {
                        backgroundValue = templateMediaUrl
                        backgroundType = templateBackgroundType.takeIf { it == "IMAGE" || it == "VIDEO" } ?: "IMAGE"
                        backgroundVisible = true
                        backgroundPlayerRetained = backgroundType == "VIDEO"
                    }
                    overlayDim = template.optInt("overlayDimPercent", overlayDim).coerceIn(0, 100)
                    lifecycleScope.launch(Dispatchers.IO) { displayPreferences.save(textColor, backgroundColorHex) }
                    lyricsVisible = displayText.isNotEmpty()
                    isBlank = !lyricsVisible && !backgroundVisible
                }
                "MEDIA_ERROR" -> Log.w("MainActivity", "Media error: ${json.optString("reason")}")
                "HEARTBEAT" -> {
                    wsServer.sendToController("""{"type":"HEARTBEAT_ACK","timestamp":${System.currentTimeMillis()}}""")
                }
            }
            if (type != "HEARTBEAT") {
                val hasLyrics = !isBlank && !isBlackScreen && displayText.isNotEmpty() && lyricsVisible
                val ok = when (type) {
                    "JUMP_SLIDE" -> json.optInt("slideIndex", -1) in slides.indices
                    "SET_BACKGROUND" -> {
                        val url = json.optString("mediaUrl")
                        json.optString("mediaId").isNotBlank() &&
                            ((url.startsWith("http://") || url.startsWith("https://")) && !url.contains("localhost") && !url.contains("127.0.0.1") || resolveMediaUrl(json.optString("mediaId")) != null)
                    }
                    else -> true
                }
                wsServer.sendToController(JSONObject().apply {
                    put("type", "ACK")
                    put("receivedMessageType", type)
                    put("success", ok)
                    put("currentSlide", currentSlideIndex)
                    put("error", if (ok) JSONObject.NULL else if (type == "SET_BACKGROUND") "Receiver media address is unavailable" else "Slide index out of bounds")
                    put("screenState", when { isBlackScreen -> "BLACK"; isBlank -> "BLANK"; else -> "CONTENT" })
                    put("layerState", JSONObject().put("background", backgroundVisible).put("lyrics", hasLyrics).put("overlay", overlayDim > 0))
                    if (type == "SET_BACKGROUND") put("mediaId", json.optString("mediaId"))
                    if (type == "HANDSHAKE") {
                        put("protocolVersion", 2)
                        put("mediaPort", 8766)
                        wsServer.getActiveLocalHost()?.let { put("mediaHost", it) }
                        put("screenWidthDp", resources.configuration.screenWidthDp)
                        put("screenHeightDp", resources.configuration.screenHeightDp)
                    }
                    put("commandTimestamp", json.optLong("timestamp", 0L))
                    put("renderTimestamp", System.currentTimeMillis())
                    put("timestamp", System.currentTimeMillis())
                }.toString())
                if (type in setOf("NEXT_SLIDE", "PREV_SLIDE", "JUMP_SLIDE")) {
                    val sent = json.optLong("timestamp", 0L)
                    if (sent > 0L) Log.d("SlideLatency", "command=$type commandToRenderMs=${(System.currentTimeMillis() - sent).coerceAtLeast(0L)} slide=$currentSlideIndex")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error parsing message: $rawJson", e)
        }
    }

    private fun loadDefaultSampleSong() {
        songTitle = "Amazing Grace"
        slides.clear()
        slides.addAll(
            listOf(
                "Amazing grace, how sweet the sound\nThat saved a wretch like me",
                "I once was lost, but now am found\nWas blind, but now I see",
                "'Twas grace that taught my heart to fear\nAnd grace my fears relieved",
                "How precious did that grace appear\nThe hour I first believed"
            )
        )
        currentSlideIndex = 0
        displayText = slides.firstOrNull().orEmpty()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releaseWifiLock()
        nsdRegistrar.unregisterService()
        wsServer.stop()
        mediaServer.stop()
        ServerKeepAliveService.stop(applicationContext)
    }

    private fun enterImmersive4kMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        // Request 4K only when the TV reports it as a supported display mode.
        runCatching {
            @Suppress("DEPRECATION")
            val mode = windowManager.defaultDisplay.supportedModes
                .filter { maxOf(it.physicalWidth, it.physicalHeight) >= 3840 && minOf(it.physicalWidth, it.physicalHeight) >= 2160 }
                .maxByOrNull { it.physicalWidth.toLong() * it.physicalHeight }
            if (mode != null) window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId }
        }.onFailure { Log.w("MainActivity", "Unable to request 4K display mode", it) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive4kMode()
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
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
            e.printStackTrace()
        }
        return "Unavailable"
    }

    private fun resolveMediaUrl(mediaId: String): String? {
        val host = if (::wsServer.isInitialized) wsServer.getActiveLocalHost() else null
        val address = host ?: getLocalIpAddress().takeIf { it != "Unavailable" }
        if (address.isNullOrBlank() || address.startsWith("127.") || address == "localhost" || address == "0.0.0.0" || address == "::1") return null
        val urlHost = if (address.contains(':')) "[${address.replace("%", "%25")}]" else address
        return "http://$urlHost:8766/media/$mediaId"
    }

    private fun acquireWifiLock() {
        try {
            if (wifiLock?.isHeld == true) return
            val manager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            wifiLock = manager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "$packageName:ReceiverWifi")
                .apply { setReferenceCounted(false); acquire() }
        } catch (error: Exception) { Log.w("BackgroundLayer", "Could not acquire receiver Wi-Fi lock", error) }
    }

    private fun releaseWifiLock() {
        try { wifiLock?.takeIf { it.isHeld }?.release() }
        catch (error: Exception) { Log.w("BackgroundLayer", "Could not release receiver Wi-Fi lock", error) }
        wifiLock = null
    }
}

@Composable
fun ReceiverScreen(
    deviceName: String,
    displayText: String,
    isConnected: Boolean,
    isBlackScreen: Boolean,
    isBlank: Boolean,
    ipAddress: String,
    port: Int,
    fontFamilyStr: String,
    fontSize: Int,
    textColorHex: String,
    backgroundType: String,
    backgroundValue: String,
    backgroundColorHex: String = backgroundValue,
    backgroundGradientEndColor: String = "#FF1E3A8A",
    backgroundVisible: Boolean = backgroundType == "IMAGE" || backgroundType == "VIDEO",
    backgroundPlayerRetained: Boolean = false,
    lyricsVisible: Boolean = !isBlank && !isBlackScreen,
    overlayDim: Int = 0,
    mediaLoop: Boolean = true,
    mediaMuted: Boolean = true,
    mediaFit: String = "FILL",
    mediaPlaying: Boolean = true,
    mediaRestartKey: Int = 0,
    textBold: Boolean = false,
    textItalic: Boolean = false,
    horizontalAlignment: String = "center",
    verticalAlignment: String = "center",
    lineSpacing: Float = 1.25f,
    shadowColorHex: String? = null,
    textPaddingDp: Int = 40
) {
    val isBlack = isConnected && (isBlackScreen || isBlank)
    val horizontal = when (horizontalAlignment.lowercase()) { "left" -> Alignment.Start; "right" -> Alignment.End; else -> Alignment.CenterHorizontally }
    val vertical = when (verticalAlignment.lowercase()) { "top" -> Alignment.Top; "bottom" -> Alignment.Bottom; else -> Alignment.CenterVertically }
    val textAlign = when (horizontalAlignment.lowercase()) { "left" -> TextAlign.Start; "right" -> TextAlign.End; else -> TextAlign.Center }
    val renderBackgroundLayer = backgroundVisible || (backgroundPlayerRetained && (isBlack || !isConnected))
    val baseBrush = if (backgroundType.equals("GRADIENT", true)) {
        Brush.verticalGradient(listOf(parseReceiverColor(backgroundColorHex, Color.Black), parseReceiverColor(backgroundGradientEndColor, Color.Black)))
    } else Brush.verticalGradient(listOf(parseReceiverColor(backgroundColorHex, Color.Black), parseReceiverColor(backgroundColorHex, Color.Black)))
    Box(Modifier.fillMaxSize().background(baseBrush)) {
        if (renderBackgroundLayer && backgroundType == "IMAGE" && backgroundValue.startsWith("http")) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current).data(backgroundValue).crossfade(350).build(),
                contentDescription = null,
                contentScale = when (mediaFit.uppercase()) { "FIT" -> ContentScale.Fit; "STRETCH" -> ContentScale.FillBounds; else -> ContentScale.Crop },
                modifier = Modifier.fillMaxSize()
            )
        } else if (renderBackgroundLayer && backgroundType == "VIDEO" && backgroundValue.startsWith("http")) {
            VideoBackgroundPlayer(videoUrl = backgroundValue, loop = mediaLoop, muted = mediaMuted, fit = mediaFit, playWhenReady = mediaPlaying, restartKey = mediaRestartKey)
        }
        if (!isBlack && backgroundVisible && overlayDim > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = overlayDim.coerceIn(0, 100) / 100f)))
        // Black is a temporary output override. Keep the media composable alive underneath
        // so video position/playback survives BLACK_SCREEN and subsequent lyric changes.
        if (isBlack) Box(Modifier.fillMaxSize().background(Color.Black))
        if (!isBlack && lyricsVisible && displayText.isNotBlank()) {
            Crossfade(targetState = displayText, animationSpec = tween(90), label = "receiver-lyrics") { slide ->
                Box(Modifier.fillMaxSize().padding(textPaddingDp.coerceAtLeast(0).dp), contentAlignment = Alignment.Center) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = horizontal, verticalArrangement = when (vertical) { Alignment.Top -> Arrangement.Top; Alignment.Bottom -> Arrangement.Bottom; else -> Arrangement.Center }) {
                        Text(
                            slide,
                            color = parseReceiverColor(textColorHex, Color.White),
                            fontSize = fontSize.sp,
                            fontFamily = receiverFont(fontFamilyStr),
                            fontWeight = if (textBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (textItalic) FontStyle.Italic else FontStyle.Normal,
                            textAlign = textAlign,
                            lineHeight = (fontSize * lineSpacing.coerceIn(0.8f, 2.5f)).sp,
                            style = TextStyle(shadow = shadowColorHex?.let { Shadow(parseReceiverColor(it, Color.Transparent), Offset(2f, 2f), 5f) })
                        )
                    }
                }
            }
        }

        if (!isConnected) {
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)
                    .background(Color(0xF010131C), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(deviceName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Disconnected", color = Color(0xFFFF4D4D), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("IP Address: $ipAddress:$port", color = Color(0xFF38BDF8), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
private fun receiverFont(name: String): FontFamily = when (name.lowercase()) {
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    "telugu", "telugu (sans)", "sans-serif-te" -> FontFamily(Typeface.create("sans-serif-te", Typeface.NORMAL))
    "telugu (serif)", "serif-te" -> FontFamily(Typeface.create("serif-te", Typeface.NORMAL))
    else -> FontFamily.SansSerif
}

private fun parseReceiverColor(value: String, fallback: Color): Color = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(fallback)

@OptIn(UnstableApi::class)
@Composable
fun VideoBackgroundPlayer(videoUrl: String, loop: Boolean = true, muted: Boolean = true, fit: String = "FILL", playWhenReady: Boolean = true, restartKey: Int = 0) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val exoPlayer = remember(context, videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
        }
    }

    LaunchedEffect(videoUrl) {
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
        exoPlayer.prepare()
    }
    LaunchedEffect(loop, muted, playWhenReady, restartKey) {
        exoPlayer.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        exoPlayer.volume = if (muted) 0f else 1f
        if (restartKey > 0) exoPlayer.seekTo(0)
        if (playWhenReady) exoPlayer.play() else exoPlayer.pause()
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
            else if (event == Lifecycle.Event.ON_START && playWhenReady) exoPlayer.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = when (fit.uppercase()) { "FIT" -> AspectRatioFrameLayout.RESIZE_MODE_FIT; "STRETCH" -> AspectRatioFrameLayout.RESIZE_MODE_FILL; else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

