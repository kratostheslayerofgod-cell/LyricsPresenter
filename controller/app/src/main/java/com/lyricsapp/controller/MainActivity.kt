package com.lyricsapp.controller

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.provider.Settings
import android.os.IBinder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.lyricsapp.controller.data.DatabaseProvider
import com.lyricsapp.controller.data.SongRepository
import com.lyricsapp.controller.data.PresenterPreferences
import com.lyricsapp.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

private val RoyalBlueDark = SpatialColors.Background
private val VividBlue = SpatialColors.Accent
private val GlowingCyan = SpatialColors.Gold
private val LightNeonBlue = SpatialColors.Info
private val PureWhite = SpatialColors.TextPrimary
private val LightBackground = SpatialColors.Background
private val LightPanel = SpatialColors.Surface
private val LightPanelElevated = SpatialColors.Raised
private val TextDark = SpatialColors.TextPrimary
private val TextMuted = SpatialColors.TextSecondary

private fun formatHttpHost(host: String): String {
    val clean = host.trim().removePrefix("/").removePrefix("[").removeSuffix("]")
    val encoded = clean.replace("%", "%25")
    return if (clean.contains(':')) "[$encoded]" else clean
}

private fun buildReceiverMediaUrl(host: String?, port: Int, mediaId: String): String? {
    if (host.isNullOrBlank()) return null
    return "http://${formatHttpHost(host)}:$port/media/$mediaId"
}

private fun backgroundCommand(mediaId: String, mediaType: String, host: String?, port: Int): String =
    JSONObject().apply {
        put("type", "SET_BACKGROUND")
        put("mediaId", mediaId)
        put("mediaType", mediaType)
        buildReceiverMediaUrl(host, port, mediaId)?.let { put("mediaUrl", it) }
        put("fit", "FILL")
        put("loop", true)
        put("muted", true)
        put("timestamp", System.currentTimeMillis())
    }.toString()

val RoyalBlueGradient = Brush.horizontalGradient(
    colors = listOf(RoyalBlueDark, VividBlue)
)

enum class ControllerTab {
    CONNECTION,
    LIVE,
    LIBRARY,
    TEMPLATES,
    MEDIA,
    CREATOR
}

class MainActivity : ComponentActivity() {
    
    private lateinit var wsClient: WebSocketClientManager
    private lateinit var discoveryManager: DiscoveryManager
    private lateinit var songRepository: SongRepository
    private var boundControlService: ConnectionKeepAliveService? = null
    private var serviceBound = false
    private var pendingConnectHost: String? = null
    private var serviceReady: ((ConnectionKeepAliveService) -> Unit)? = null
    private val controlServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? ConnectionKeepAliveService.LocalBinder)?.service() ?: return
            boundControlService = service
            serviceReady?.invoke(service)
            pendingConnectHost?.also { pendingConnectHost = null; service.webSocketClient.connect(it) }
        }
        override fun onServiceDisconnected(name: ComponentName?) { boundControlService = null }
    }

    private fun connectReceiver(host: String) {
        pendingConnectHost = host
        ConnectionKeepAliveService.start(applicationContext, restoreSavedConnection = false)
        boundControlService?.webSocketClient?.connect(host)
        if (boundControlService != null) pendingConnectHost = null
    }

    private fun sendToReceiver(message: String) {
        if (::wsClient.isInitialized) wsClient.sendMessage(message)
        else Log.w("PresentationService", "Command ignored while connection service is binding")
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        songRepository = SongRepository(DatabaseProvider.get(applicationContext))
        val presenterPreferences = PresenterPreferences(applicationContext)
        var templates by mutableStateOf(builtInTemplates())
        var mediaAssets by mutableStateOf(emptyList<MediaAsset>())
        lifecycleScope.launch(Dispatchers.IO) {
            presenterPreferences.templates.collect { saved -> if (saved.isNotEmpty()) withContext(Dispatchers.Main) { templates = saved } }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            presenterPreferences.media.collect { saved -> withContext(Dispatchers.Main) { mediaAssets = saved } }
        }
        
        var isConnected by mutableStateOf(false)
        var connectedDeviceName by mutableStateOf<String?>(null)
        var connectedDeviceHost by mutableStateOf<String?>(null)
        var connectionStatus by mutableStateOf("Disconnected")
        var receiverScreenState by mutableStateOf("BLANK")
        var receiverMediaPort by mutableIntStateOf(8766)
        var receiverScreenWidthDp by mutableIntStateOf(1920)
        var appMessage by mutableStateOf<AppMessage?>(null)
        var wasConnected by mutableStateOf(false)
        
        val discoveredReceivers = mutableStateListOf<DiscoveryManager.DiscoveredReceiver>()
        var isSearching by mutableStateOf(true)
        
        var selectedTab by mutableStateOf(ControllerTab.CONNECTION)
        var activeSong by mutableStateOf<Song?>(null)
        var activeSlideIndex by mutableStateOf(0)
        var editingSong by mutableStateOf<Song?>(null)
        
        // Observe Room DB songs
        val savedSongsFlow = songRepository.observeSongs()
        
        // Seed default songs if database is empty
        lifecycleScope.launch(Dispatchers.IO) {
            savedSongsFlow.collect { songs ->
                if (songs.isEmpty()) {
                    seedDefaultSongs()
                } else if (activeSong == null) {
                    activeSong = songs.first()
                }
            }
        }

        serviceReady = { service ->
            wsClient = service.webSocketClient
            lifecycleScope.launch {
                service.incomingMessages.collectLatest { message ->
                runCatching {
                    val incoming = JSONObject(message)
                    if (incoming.optString("receivedMessageType") == "HANDSHAKE") receiverMediaPort = incoming.optInt("mediaPort", 8766)
                    if (incoming.optString("receivedMessageType") == "HANDSHAKE") receiverScreenWidthDp = incoming.optInt("screenWidthDp", receiverScreenWidthDp).coerceAtLeast(1)
                    when (incoming.optString("type")) {
                        "ACK" -> {
                            receiverScreenState = incoming.optString("screenState", receiverScreenState)
                            val command = incoming.optString("receivedMessageType")
                            appMessage = if (incoming.optBoolean("success", true)) AppMessages.success(applicationContext, command)
                            else AppMessages.failedAck(command, incoming.optString("error").takeIf { it.isNotBlank() })
                        }
                        "MEDIA_ERROR" -> appMessage = AppMessages.mediaFailure(incoming.optString("reason", ""))
                        "MEDIA_READY" -> appMessage = AppMessage(R.string.msg_media_ready, R.string.msg_media_ready_cause, severity = MessageSeverity.Success)
                    }
                }.onFailure { error ->
                    Log.w("MainActivity", "Could not read receiver message", error)
                    appMessage = AppMessages.genericFailure()
                }
                }
            }
            lifecycleScope.launch {
                service.connectionState.collectLatest { connected ->
                isConnected = connected
                if (connected) {
                    val name = connectedDeviceName ?: "TV Receiver"
                    connectionStatus = "Connected to 📺 $name"
                    if (wasConnected) appMessage = AppMessages.backOnline()
                    wasConnected = true
                } else {
                    connectionStatus = "Disconnected (Retrying...)"
                    if (wasConnected) appMessage = AppMessages.connectionProblem()
                }
                }
            }
            lifecycleScope.launch {
                service.repeatedDisconnects.collectLatest { count ->
                    if (count > 0) appMessage = AppMessage(
                        title = R.string.msg_background_limits_title,
                        cause = R.string.msg_background_limits_cause,
                        actionLabel = R.string.action_battery_settings,
                        action = AppMessageAction.OpenBatterySettings,
                        severity = MessageSeverity.Warning
                    )
                }
            }
        }
        
        discoveryManager = DiscoveryManager(applicationContext) { list ->
            discoveredReceivers.clear()
            discoveredReceivers.addAll(list)
            isSearching = false
        }

        setContent {
            val savedSongs by savedSongsFlow.collectAsState(initial = emptyList())
            var isImportingSongs by remember { mutableStateOf(false) }
            var showImportTemplateDialog by remember { mutableStateOf(false) }
            var pendingTxtUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
            var importTemplateId by remember { mutableStateOf(templates.firstOrNull { it.isDefault }?.id) }

            val bulkTxtPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenMultipleDocuments()
            ) { uris ->
                if (!uris.isNullOrEmpty()) {
                    pendingTxtUris = uris.toList()
                    importTemplateId = templates.firstOrNull { it.isDefault }?.id
                    showImportTemplateDialog = true
                }
            }

            if (showImportTemplateDialog) {
                AlertDialog(
                    onDismissRequest = { showImportTemplateDialog = false },
                    title = { Text("Import songs with a template") },
                    text = {
                        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                            Text("Choose the style to apply to every imported song.", color = TextMuted)
                            templates.forEach { template ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { importTemplateId = template.id }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = importTemplateId == template.id, onClick = { importTemplateId = template.id })
                                    Column(Modifier.padding(start = 8.dp)) {
                                        Text(template.name, color = TextDark)
                                        Text("${template.fontSize} sp · ${template.horizontalAlignment} · ${template.backgroundType.name.lowercase()}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val chosenTemplate = templates.firstOrNull { it.id == importTemplateId }
                            val urisToImport = pendingTxtUris
                            pendingTxtUris = emptyList()
                            showImportTemplateDialog = false
                            isImportingSongs = true
                            lifecycleScope.launch(Dispatchers.IO) {
                                var importedCount = 0
                                for (uri in urisToImport) {
                                    try {
                                        val fileName = getFileNameFromUri(uri)
                                        val title = fileName.removeSuffix(".txt").removeSuffix(".TXT").trim().ifBlank { "Untitled Song" }
                                        val lyricsContent = contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                                        if (!lyricsContent.isNullOrBlank()) {
                                            val sections = parseLyricsToSections(lyricsContent)
                                            if (sections.isNotEmpty()) {
                                                songRepository.saveSong(Song(
                                                    title = title,
                                                    sections = sections,
                                                    displaySettings = chosenTemplate?.toDisplaySettings() ?: DisplaySettings()
                                                ))
                                                importedCount++
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Error importing TXT file: $uri", e)
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    isImportingSongs = false
                                    appMessage = AppMessage(R.string.msg_import_complete, R.string.msg_import_complete_cause, severity = MessageSeverity.Success)
                                }
                            }
                        }) { Text("Import ${pendingTxtUris.size} songs") }
                    },
                    dismissButton = { TextButton(onClick = { showImportTemplateDialog = false; pendingTxtUris = emptyList() }) { Text("Cancel") } },
                    containerColor = LightPanel
                )
            }

            if (isImportingSongs) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    title = { Text("Importing Songs...", fontWeight = FontWeight.Bold, color = VividBlue) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator(color = VividBlue, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Parsing .txt files and saving to library...", color = TextDark)
                        }
                    },
                    containerColor = LightPanel
                )
            }
            
            LyricsControllerTheme {
                SpatialBackdrop {
                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            containerColor = SpatialColors.Floating.copy(alpha = 0.96f),
                            contentColor = PureWhite
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == ControllerTab.CONNECTION,
                                onClick = { selectedTab = ControllerTab.CONNECTION },
                                icon = { Icon(Icons.Default.SettingsInputComponent, contentDescription = "Connect") },
                                label = { Text("Connect") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = GlowingCyan,
                                    selectedTextColor = GlowingCyan,
                                    unselectedIconColor = PureWhite.copy(alpha = 0.7f),
                                    unselectedTextColor = PureWhite.copy(alpha = 0.7f),
                                    indicatorColor = VividBlue
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == ControllerTab.LIVE,
                                onClick = { selectedTab = ControllerTab.LIVE },
                                icon = { Icon(Icons.Default.Tv, contentDescription = "Live") },
                                label = { Text("Live") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = GlowingCyan,
                                    selectedTextColor = GlowingCyan,
                                    unselectedIconColor = PureWhite.copy(alpha = 0.7f),
                                    unselectedTextColor = PureWhite.copy(alpha = 0.7f),
                                    indicatorColor = VividBlue
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == ControllerTab.LIBRARY,
                                onClick = { selectedTab = ControllerTab.LIBRARY },
                                icon = { Icon(Icons.Default.LibraryBooks, contentDescription = "Library") },
                                label = { Text("Library") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = GlowingCyan,
                                    selectedTextColor = GlowingCyan,
                                    unselectedIconColor = PureWhite.copy(alpha = 0.7f),
                                    unselectedTextColor = PureWhite.copy(alpha = 0.7f),
                                    indicatorColor = VividBlue
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == ControllerTab.CREATOR,
                                onClick = { 
                                    editingSong = null
                                    selectedTab = ControllerTab.CREATOR 
                                },
                                icon = { Icon(Icons.Default.Create, contentDescription = "Create") },
                                label = { Text("Create") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = GlowingCyan,
                                    selectedTextColor = GlowingCyan,
                                    unselectedIconColor = PureWhite.copy(alpha = 0.7f),
                                    unselectedTextColor = PureWhite.copy(alpha = 0.7f),
                                    indicatorColor = VividBlue
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == ControllerTab.TEMPLATES,
                                onClick = { selectedTab = ControllerTab.TEMPLATES },
                                icon = { Icon(Icons.Default.Palette, contentDescription = "Templates") },
                                label = { Text("Styles") },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = GlowingCyan, selectedTextColor = GlowingCyan, unselectedIconColor = PureWhite.copy(alpha = 0.7f), unselectedTextColor = PureWhite.copy(alpha = 0.7f), indicatorColor = VividBlue)
                            )
                            NavigationBarItem(
                                selected = selectedTab == ControllerTab.MEDIA,
                                onClick = { selectedTab = ControllerTab.MEDIA },
                                icon = { Icon(Icons.Default.Wallpaper, contentDescription = "Media") },
                                label = { Text("Media") },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = GlowingCyan, selectedTextColor = GlowingCyan, unselectedIconColor = PureWhite.copy(alpha = 0.7f), unselectedTextColor = PureWhite.copy(alpha = 0.7f), indicatorColor = VividBlue)
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        when (selectedTab) {
                            ControllerTab.CONNECTION -> {
                                ConnectionScreen(
                                    isConnected = isConnected,
                                    connectedDeviceName = connectedDeviceName,
                                    connectedDeviceHost = connectedDeviceHost,
                                    connectionStatus = connectionStatus,
                                    discoveredReceivers = discoveredReceivers,
                                    isSearching = isSearching,
                                    onRefreshDiscovery = {
                                        isSearching = true
                                        discoveryManager.startDiscovery()
                                    },
                                    onConnectDevice = { name, host ->
                                        connectedDeviceName = name
                                        connectedDeviceHost = host
                                        connectionStatus = "Connecting to $name ($host)..."
                                        connectReceiver(host)
                                    },
                                    onDisconnectDevice = {
                                        if (::wsClient.isInitialized) wsClient.disconnect()
                                        ConnectionKeepAliveService.stop(applicationContext)
                                        isConnected = false
                                        connectedDeviceName = null
                                        connectedDeviceHost = null
                                        connectionStatus = "Disconnected"
                                    }
                                )
                            }
                            ControllerTab.LIVE -> {
                                LivePresenterScreen(
                                    activeSong = activeSong,
                                    activeSlideIndex = activeSlideIndex,
                                    isConnected = isConnected,
                                    screenState = receiverScreenState,
                                    availableTemplates = templates,
                                    onSelectSlide = { slideIndex ->
                                        activeSlideIndex = slideIndex
                                        activeSong?.let { song ->
                                            if (isConnected && ::wsClient.isInitialized && slideIndex in song.slides.indices) {
                                                sendSongDisplayMessage(wsClient, song, slideIndex, connectedDeviceHost, receiverMediaPort)
                                            }
                                        }
                                    },
                                    onUpdateActiveSong = { updatedSong ->
                                        activeSong = updatedSong
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            songRepository.saveSong(updatedSong)
                                        }
                                        if (isConnected && ::wsClient.isInitialized) {
                                            sendSongDisplayMessage(wsClient, updatedSong, activeSlideIndex.coerceIn(0, maxOf(0, updatedSong.slides.size - 1)), connectedDeviceHost, receiverMediaPort)
                                        }
                                    },
                                    onNextSlide = {
                                        activeSong?.slides?.takeIf { it.isNotEmpty() }?.let { slides ->
                                            activeSlideIndex = (activeSlideIndex + 1).coerceAtMost(slides.lastIndex)
                                        }
                                        sendToReceiver("""{"type":"NEXT_SLIDE","timestamp":${System.currentTimeMillis()}}""")
                                    },
                                    onPrevSlide = {
                                        activeSong?.slides?.takeIf { it.isNotEmpty() }?.let { slides ->
                                            activeSlideIndex = (activeSlideIndex - 1).coerceIn(slides.indices)
                                        }
                                        sendToReceiver("""{"type":"PREV_SLIDE","timestamp":${System.currentTimeMillis()}}""")
                                    },
                                    onBlackScreen = {
                                        sendToReceiver("""{"type":"BLACK_SCREEN","timestamp":${System.currentTimeMillis()}}""")
                                    },
                                    onClearScreen = {
                                        sendToReceiver("""{"type":"CLEAR_SCREEN","timestamp":${System.currentTimeMillis()}}""")
                                    },
                                    onClearBackground = { sendToReceiver("""{"type":"CLEAR_BACKGROUND","timestamp":${System.currentTimeMillis()}}""") },
                                    onClearLyrics = { sendToReceiver("""{"type":"CLEAR_LYRICS","timestamp":${System.currentTimeMillis()}}""") },
                                    onClearOverlay = { sendToReceiver("""{"type":"CLEAR_OVERLAY","timestamp":${System.currentTimeMillis()}}""") },
                                    onClearAll = { sendToReceiver("""{"type":"CLEAR_ALL","timestamp":${System.currentTimeMillis()}}""") },
                                    onSetSongBackground = {
                                        val id = activeSong?.displaySettings?.backgroundMediaId
                                        if (!id.isNullOrBlank()) {
                                            sendToReceiver("""{"type":"CLEAR_LYRICS","timestamp":${System.currentTimeMillis()}}""")
                                            activeSong?.let { song -> sendToReceiver(backgroundCommand(id, song.displaySettings.backgroundType, connectedDeviceHost, receiverMediaPort)) }
                                        }
                                    },
                                    onBackgroundWithLyrics = {
                                        val id = activeSong?.displaySettings?.backgroundMediaId
                                        activeSong?.takeIf { ::wsClient.isInitialized }?.let { song -> sendSongDisplayMessage(wsClient, song, activeSlideIndex, connectedDeviceHost, receiverMediaPort) }
                                        if (!id.isNullOrBlank()) activeSong?.let { song -> sendToReceiver(backgroundCommand(id, song.displaySettings.backgroundType, connectedDeviceHost, receiverMediaPort)) }
                                    },
                                    onVideoControl = { command, enabled -> sendToReceiver("""{"type":"$command","enabled":$enabled,"timestamp":${System.currentTimeMillis()}}""") },
                                    onApplyTemplate = { template ->
                                        val payload = JSONObject().apply {
                                            put("id", template.id); put("name", template.name); put("isDefault", template.isDefault); put("isBuiltIn", template.isBuiltIn)
                                            put("fontFamily", template.fontFamily); put("fontSize", template.fontSize); put("bold", template.bold); put("italic", template.italic)
                                            put("textColor", template.textColor); put("horizontalAlignment", template.horizontalAlignment); put("verticalAlignment", template.verticalAlignment)
                                            put("lineSpacing", template.lineSpacing); put("backgroundType", template.backgroundType.name); put("backgroundColor", template.backgroundColor)
                                            put("backgroundMediaId", template.backgroundMediaId); put("mediaFitMode", template.mediaFitMode.name); put("overlayDimPercent", template.overlayDimPercent)
                                            put("paddingDp", template.paddingDp); put("marginDp", template.marginDp)
                                            template.backgroundMediaId?.let { id -> buildReceiverMediaUrl(connectedDeviceHost, receiverMediaPort, id)?.let { put("backgroundMediaUrl", it) } }
                                        }
                                        sendToReceiver(JSONObject().put("type", "APPLY_TEMPLATE").put("template", payload).put("timestamp", System.currentTimeMillis()).toString())
                                    }
                                )
                            }
                            ControllerTab.LIBRARY -> {
                                SongLibraryScreen(
                                    songs = savedSongs,
                                    activeSong = activeSong,
                                    onSelectSong = { song ->
                                        activeSong = song
                                        activeSlideIndex = 0
                                        selectedTab = ControllerTab.LIVE
                                        if (isConnected && ::wsClient.isInitialized) {
                                            sendSongDisplayMessage(wsClient, song, 0, connectedDeviceHost, receiverMediaPort)
                                        }
                                    },
                                    onEditSong = { song ->
                                        editingSong = song
                                        selectedTab = ControllerTab.CREATOR
                                    },
                                    onDeleteSong = { song ->
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            songRepository.deleteSong(song)
                                        }
                                    },
                                    onCreateNew = {
                                        editingSong = null
                                        selectedTab = ControllerTab.CREATOR
                                    },
                                    onImportTxtFiles = {
                                        bulkTxtPickerLauncher.launch(arrayOf("text/plain", "*/*"))
                                    }
                                )
                            }
                            ControllerTab.CREATOR -> {
                                FreeShowCreatorScreen(
                                    existingSong = editingSong,
                                    mediaHost = connectedDeviceHost,
                                    mediaPort = receiverMediaPort,
                                    receiverWidthDp = receiverScreenWidthDp,
                                    availableTemplates = templates,
                                    defaultTemplate = editingSong?.displaySettings?.templateId?.let { id -> templates.firstOrNull { it.id == id } }
                                        ?: if (editingSong == null) templates.firstOrNull { it.isDefault } else null,
                                    onColorChange = { text, background ->
                                        if (isConnected && ::wsClient.isInitialized) {
                                            val update = JSONObject().apply {
                                                text?.let { put("textColor", it) }; background?.let { put("bgColor", it) }
                                                put("timestamp", System.currentTimeMillis())
                                            }
                                            update.put("type", "UPDATE_STYLE")
                                            sendToReceiver(update.toString())
                                        }
                                    },
                                    onMessage = { appMessage = it },
                                    onSaveSong = { newSong ->
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            songRepository.saveSong(newSong)
                                        }
                                        activeSong = newSong
                                        activeSlideIndex = 0
                                        selectedTab = ControllerTab.LIVE
                                        if (isConnected && ::wsClient.isInitialized) {
                                            sendSongDisplayMessage(wsClient, newSong, 0, connectedDeviceHost, receiverMediaPort)
                                        }
                                    }
                                )
                            }
                            ControllerTab.TEMPLATES -> TemplateManagerScreen(
                                templates = templates,
                                onSave = { template ->
                                    val updated = if (templates.any { it.id == template.id }) templates.map { if (it.id == template.id) template else it } else templates + template
                                    templates = updated
                                    lifecycleScope.launch(Dispatchers.IO) { presenterPreferences.saveTemplates(updated) }
                                },
                                onDelete = { template ->
                                    val updated = templates.filterNot { it.id == template.id }
                                    templates = updated
                                    lifecycleScope.launch(Dispatchers.IO) { presenterPreferences.saveTemplates(updated) }
                                },
                                onSetDefault = { selected ->
                                    val updated = templates.map { it.copy(isDefault = it.id == selected.id) }
                                    templates = updated
                                    lifecycleScope.launch(Dispatchers.IO) { presenterPreferences.saveTemplates(updated) }
                                },
                                onApply = { template ->
                                    if (::wsClient.isInitialized) sendTemplateMessage(wsClient, template, connectedDeviceHost, receiverMediaPort)
                                    activeSong?.let { song ->
                                        val style = song.displaySettings.copy(
                                            fontFamily = template.fontFamily, fontSize = template.fontSize,
                                            textColor = template.textColor, backgroundType = if (template.backgroundType.name == "SOLID") "COLOR" else template.backgroundType.name,
                                            backgroundValue = template.backgroundColor, textBold = template.bold, textItalic = template.italic,
                                            horizontalAlignment = template.horizontalAlignment, verticalAlignment = template.verticalAlignment,
                                            lineSpacing = template.lineSpacing, shadowColor = template.shadowColor,
                                            backgroundAlpha = 255 - (template.overlayDimPercent.coerceIn(0, 100) * 255 / 100),
                                            backgroundMediaId = template.backgroundMediaId, mediaFitMode = template.mediaFitMode,
                                            templateId = template.id, paddingDp = template.paddingDp
                                        )
                                        val updated = song.copy(displaySettings = style, updatedAt = System.currentTimeMillis())
                                        activeSong = updated
                                        lifecycleScope.launch(Dispatchers.IO) { songRepository.saveSong(updated) }
                                    }
                                }
                            )
                            ControllerTab.MEDIA -> MediaLibraryScreen(
                                assets = mediaAssets,
                                mediaHost = connectedDeviceHost,
                                mediaPort = receiverMediaPort,
                                onImported = { asset ->
                                    val updated = (mediaAssets.filterNot { it.mediaId == asset.mediaId } + asset)
                                    mediaAssets = updated
                                    lifecycleScope.launch(Dispatchers.IO) { presenterPreferences.saveMedia(updated) }
                                },
                                onMessage = { appMessage = it },
                                onSetBackground = { asset, showLyrics ->
                                    if (showLyrics && ::wsClient.isInitialized) activeSong?.let { song -> sendSongDisplayMessage(wsClient, song, activeSlideIndex.coerceIn(0, maxOf(0, song.slides.lastIndex)), connectedDeviceHost, receiverMediaPort) }
                                    else sendToReceiver("""{"type":"CLEAR_LYRICS","timestamp":${System.currentTimeMillis()}}""")
                                    sendToReceiver(backgroundCommand(asset.mediaId, asset.type, connectedDeviceHost, receiverMediaPort))
                                }
                            )
                        }
                    }
                }
                AppMessageHost(
                    message = appMessage,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onDismiss = { appMessage = null },
                    onAction = { action ->
                        when (action) {
                            AppMessageAction.OpenConnection, AppMessageAction.TryHotspot -> selectedTab = ControllerTab.CONNECTION
                            AppMessageAction.OpenBatterySettings -> runCatching {
                                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }.onFailure { Log.w("PresentationService", "Battery settings could not be opened", it) }
                            AppMessageAction.RetryConnection -> connectedDeviceHost?.let(::connectReceiver)
                            AppMessageAction.OpenMedia -> selectedTab = ControllerTab.MEDIA
                            AppMessageAction.ReviewSong -> selectedTab = ControllerTab.LIBRARY
                            AppMessageAction.SaveSong -> selectedTab = ControllerTab.CREATOR
                            AppMessageAction.DownloadBible, AppMessageAction.None -> Unit
                        }
                    }
                )
                }
            }
        }
    }

    private fun seedDefaultSongs() {
        lifecycleScope.launch(Dispatchers.IO) {
            val s1 = Song(
                id = "song-amazing-grace",
                title = "Amazing Grace",
                author = "John Newton",
                sections = parseLyricsToSections("""
                    [Verse 1]
                    Amazing grace, how sweet the sound
                    That saved a wretch like me
                    
                    I once was lost, but now am found
                    Was blind, but now I see
                    
                    [Verse 2]
                    'Twas grace that taught my heart to fear
                    And grace my fears relieved
                    
                    How precious did that grace appear
                    The hour I first believed
                    
                    [Chorus]
                    My chains are gone, I've been set free
                    My God, my Savior has ransomed me
                    And like a flood His mercy rains
                    Unending love, amazing grace
                """.trimIndent()),
                displaySettings = DisplaySettings(
                    fontFamily = "SansSerif",
                    fontSize = 48,
                    textColor = "#00E5FF",
                    backgroundType = "COLOR",
                    backgroundValue = "#1E3A8A"
                )
            )

            val s2 = Song(
                id = "song-how-great-is-our-god",
                title = "How Great Is Our God",
                author = "Chris Tomlin",
                sections = parseLyricsToSections("""
                    [Verse 1]
                    The splendor of the King, clothed in majesty
                    Let all the earth rejoice, all the earth rejoice
                    
                    He wraps Himself in light, and darkness tries to hide
                    And trembles at His voice, and trembles at His voice
                    
                    [Chorus]
                    How great is our God, sing with me
                    How great is our God, and all will see
                    How great, how great is our God
                    
                    [Bridge]
                    Name above all names, worthy of all praise
                    My heart will sing, how great is our God
                """.trimIndent()),
                displaySettings = DisplaySettings(
                    fontFamily = "SansSerif",
                    fontSize = 48,
                    textColor = "#FFFFFF",
                    backgroundType = "COLOR",
                    backgroundValue = "#1E3A8A"
                )
            )

            songRepository.saveSong(s1)
            songRepository.saveSong(s2)
        }
    }

    private fun sendSongDisplayMessage(
        wsClient: WebSocketClientManager,
        song: Song,
        slideIndex: Int = 0,
        receiverHost: String? = null,
        mediaPort: Int = 8766
    ) {
        val slidesJson = song.slides.mapIndexed { idx, slide ->
            """{ "index": $idx, "content": ${JSONObject.quote(slide.content)} }"""
        }.joinToString(",")
        
        val settings = song.displaySettings
        val settingsJson = """{
            "fontFamily": ${JSONObject.quote(settings.fontFamily)},
            "fontSize": ${settings.fontSize},
            "textColor": ${JSONObject.quote(settings.textColor)},
            "backgroundType": ${JSONObject.quote(settings.backgroundType)},
            "backgroundValue": ${JSONObject.quote(settings.backgroundValue)},
            "mediaFitMode": ${JSONObject.quote(settings.mediaFitMode.name)},
            "textPaddingDp": ${settings.paddingDp},
            "templateId": ${settings.templateId?.let { JSONObject.quote(it) } ?: "null"},
            "textBold": ${settings.textBold}, "textItalic": ${settings.textItalic},
            "horizontalAlignment": ${JSONObject.quote(settings.horizontalAlignment)},
            "verticalAlignment": ${JSONObject.quote(settings.verticalAlignment)},
            "lineSpacing": ${settings.lineSpacing}, "shadowColor": ${settings.shadowColor?.let { JSONObject.quote(it) } ?: "null"},
            "overlayDimPercent": ${(255 - settings.backgroundAlpha).coerceIn(0, 255) * 100 / 255}
        }"""

        val json = """{
            "type": "DISPLAY",
            "songId": ${JSONObject.quote(song.id)},
            "songTitle": ${JSONObject.quote(song.title)},
            "currentSlide": $slideIndex,
            "totalSlides": ${song.slides.size},
            "slides": [$slidesJson],
            "displaySettings": $settingsJson,
            "timestamp": ${System.currentTimeMillis()}
        }"""

        wsClient.sendMessage(json)
        settings.backgroundMediaId?.takeIf { settings.backgroundType == "IMAGE" || settings.backgroundType == "VIDEO" }?.let { mediaId ->
            wsClient.sendMessage(backgroundCommand(mediaId, settings.backgroundType, receiverHost, mediaPort))
        }
    }

    private fun sendTemplateMessage(wsClient: WebSocketClientManager, template: Template, receiverHost: String? = null, mediaPort: Int = 8766) {
        if (!this::wsClient.isInitialized) return
        val payload = JSONObject().apply {
            put("id", template.id); put("name", template.name); put("isDefault", template.isDefault); put("isBuiltIn", template.isBuiltIn)
            put("fontFamily", template.fontFamily); put("fontSize", template.fontSize); put("bold", template.bold); put("italic", template.italic)
            put("textColor", template.textColor); put("horizontalAlignment", template.horizontalAlignment); put("verticalAlignment", template.verticalAlignment)
            put("lineSpacing", template.lineSpacing); put("backgroundType", template.backgroundType.name); put("backgroundColor", template.backgroundColor)
            put("backgroundMediaId", template.backgroundMediaId); put("mediaFitMode", template.mediaFitMode.name); put("overlayDimPercent", template.overlayDimPercent)
            put("paddingDp", template.paddingDp); put("marginDp", template.marginDp)
            template.backgroundMediaId?.let { id -> buildReceiverMediaUrl(receiverHost, mediaPort, id)?.let { put("backgroundMediaUrl", it) } }
        }
        wsClient.sendMessage(JSONObject().put("type", "APPLY_TEMPLATE").put("template", payload).put("timestamp", System.currentTimeMillis()).toString())
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Error querying file name from URI", e)
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "Song.txt"
    }

    override fun onResume() {
        super.onResume()
        discoveryManager.startDiscovery()
    }

    override fun onStart() {
        super.onStart()
        if (!serviceBound) {
            ConnectionKeepAliveService.start(applicationContext, restoreSavedConnection = true)
            serviceBound = bindService(Intent(this, ConnectionKeepAliveService::class.java), controlServiceConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        if (serviceBound) {
            unbindService(controlServiceConnection)
            serviceBound = false
            boundControlService = null
        }
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        discoveryManager.stopDiscovery()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        discoveryManager.stopDiscovery()
    }
}

// ============================================================================
// CONNECTION SCREEN
// ============================================================================

@Composable
fun ConnectionScreen(
    isConnected: Boolean,
    connectedDeviceName: String?,
    connectedDeviceHost: String?,
    connectionStatus: String,
    discoveredReceivers: List<DiscoveryManager.DiscoveredReceiver>,
    isSearching: Boolean,
    onRefreshDiscovery: () -> Unit,
    onConnectDevice: (name: String, host: String) -> Unit,
    onDisconnectDevice: () -> Unit
) {
    var ipText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Status Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Connect to TV",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = VividBlue
            )
            Surface(
                color = if (isConnected) RoyalBlueDark else MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = connectionStatus,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) GlowingCyan else PureWhite,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Single Connected Device Banner ---
        if (isConnected && connectedDeviceName != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RoyalBlueGradient, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = GlowingCyan,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = RoyalBlueDark,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "CONNECTED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RoyalBlueDark
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Tv, contentDescription = null, tint = PureWhite)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = connectedDeviceName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        }
                        if (connectedDeviceHost != null) {
                            Text(
                                text = "IP: $connectedDeviceHost:8765",
                                fontSize = 12.sp,
                                color = LightNeonBlue,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    TextButton(onClick = onDisconnectDevice) {
                        Text("Disconnect", fontSize = 12.sp, color = GlowingCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- Discovered TV Devices Bar ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SpatialColors.InnerBorder, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = LightPanel)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TV Devices on Network",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    IconButton(onClick = onRefreshDiscovery, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = VividBlue)
                    }
                }

                if (discoveredReceivers.isEmpty()) {
                    Text(
                        text = if (isSearching) "Searching for TV..." else "No TV found nearby",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    discoveredReceivers.forEach { receiver ->
                        val isThisDeviceConnected = isConnected && connectedDeviceHost == receiver.host
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Tv, contentDescription = null, tint = VividBlue, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = receiver.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                }
                                Text(
                                    text = "${receiver.host}:${receiver.port}",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                            if (isThisDeviceConnected) {
                                Surface(
                                    color = RoyalBlueDark,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GlowingCyan, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "ACTIVE",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GlowingCyan
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { onConnectDevice(receiver.name, receiver.host) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = VividBlue,
                                        contentColor = PureWhite
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(if (isConnected) "Switch" else "Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Direct IP Connection (Always visible for easy direct connection across Wi-Fi)
                Text(
                    text = "Direct IP Connection",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = VividBlue
                )
                Text(
                    text = "Enter the Receiver IP address shown on your TV/Receiver screen:",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ipText,
                        onValueChange = { ipText = it },
                        label = { Text("Receiver IP (e.g. 192.168.1.9)", fontSize = 12.sp) },
                        placeholder = { Text("192.168.1.9", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (ipText.isNotBlank()) {
                                onConnectDevice("TV Receiver ($ipText)", ipText)
                            }
                        },
                        enabled = ipText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VividBlue,
                            contentColor = PureWhite
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// TAB 1: LIVE PRESENTER SCREEN WITH QUICK EDIT
// ============================================================================

@Composable
fun LivePresenterScreen(
    activeSong: Song?,
    activeSlideIndex: Int,
    isConnected: Boolean,
    screenState: String = "BLANK",
    availableTemplates: List<Template> = builtInTemplates(),
    onSelectSlide: (Int) -> Unit,
    onUpdateActiveSong: (Song) -> Unit,
    onNextSlide: () -> Unit,
    onPrevSlide: () -> Unit,
    onBlackScreen: () -> Unit,
    onClearScreen: () -> Unit,
    onClearBackground: () -> Unit = {},
    onClearLyrics: () -> Unit = {},
    onClearOverlay: () -> Unit = {},
    onClearAll: () -> Unit = {},
    onSetSongBackground: () -> Unit = {},
    onBackgroundWithLyrics: () -> Unit = {},
    onVideoControl: (String, Boolean) -> Unit = { _, _ -> },
    onApplyTemplate: (Template) -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val isBlackScreenActive = screenState == "BLACK"
    var loopEnabled by remember { mutableStateOf(true) }
    var muteEnabled by remember { mutableStateOf(true) }
    var showQuickEditDialog by remember { mutableStateOf(false) }
    var liveOptionsExpanded by remember { mutableStateOf(false) }

    // Quick Edit Live Dialog
    if (showQuickEditDialog && activeSong != null) {
        val initialText = remember(activeSong) {
            activeSong.sections.joinToString("\n\n") { section ->
                "[${section.label}]\n" + section.slides.joinToString("\n\n") { it.content }
            }
        }
        var liveEditedText by remember { mutableStateOf(initialText) }

        AlertDialog(
            onDismissRequest = { showQuickEditDialog = false },
            containerColor = LightPanel,
            titleContentColor = VividBlue,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = VividBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Live Edit Lyrics", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Edit lyrics below. Tapping update will push changes directly to the TV screen live!",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = liveEditedText,
                        onValueChange = { liveEditedText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedSections = parseLyricsToSections(liveEditedText)
                        val updatedSong = activeSong.copy(
                            sections = updatedSections,
                            updatedAt = System.currentTimeMillis()
                        )
                        onUpdateActiveSong(updatedSong)
                        showQuickEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VividBlue, contentColor = PureWhite)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp), tint = GlowingCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Update & Push Live", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickEditDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Status Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Control",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = VividBlue
            )
            Surface(
                color = if (isConnected) RoyalBlueDark else MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = if (isConnected) "CONNECTED" else "DISCONNECTED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) GlowingCyan else PureWhite,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Active Song Banner with Quick Edit ---
        if (activeSong != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RoyalBlueGradient, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = GlowingCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeSong.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        }
                        if (!activeSong.author.isNullOrEmpty()) {
                            Text(
                                text = "By ${activeSong.author}",
                                fontSize = 13.sp,
                                color = LightNeonBlue
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Slide ${activeSlideIndex + 1} / ${activeSong.slides.size}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlowingCyan
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (activeSong != null && activeSong.slides.isNotEmpty()) {
            val style = activeSong.displaySettings
            LyricsSlidePreview(
                text = activeSong.slides.getOrNull(activeSlideIndex)?.content.orEmpty(),
                fontFamilyName = style.fontFamily,
                fontSizeSp = style.fontSize.toFloat(),
                textColorHex = style.textColor,
                backgroundColorHex = style.backgroundValue,
                receiverWidthDp = 1920,
                horizontalAlignment = style.horizontalAlignment,
                verticalAlignment = style.verticalAlignment,
                bold = style.textBold,
                italic = style.textItalic,
                lineSpacing = style.lineSpacing,
                paddingDp = style.paddingDp,
                black = screenState == "BLACK",
                modifier = Modifier.shadow(14.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
            )
        }

        if (activeSong != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { liveOptionsExpanded = !liveOptionsExpanded },
                shape = RoundedCornerShape(16.dp),
                color = SpatialColors.Surface,
                border = BorderStroke(1.dp, SpatialColors.InnerBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Live options", color = SpatialColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Templates, layers, background and video", color = SpatialColors.TextSecondary, fontSize = 12.sp)
                    }
                    Icon(
                        imageVector = if (liveOptionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (liveOptionsExpanded) "Collapse live options" else "Expand live options",
                        tint = SpatialColors.Accent
                    )
                }
            }
            AnimatedVisibility(visible = liveOptionsExpanded, enter = fadeIn(), exit = fadeOut()) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(onClick = { showQuickEditDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = SpatialColors.Accent)
                        Spacer(Modifier.width(8.dp))
                        Text("Quick edit lyrics")
                    }
                    if (isConnected) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableTemplates.forEach { template ->
                            SuggestionChip(onClick = { onApplyTemplate(template) }, label = { Text(template.name, maxLines = 1) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onSetSongBackground, modifier = Modifier.weight(1f)) { Text("Play background") }
                        OutlinedButton(onClick = onClearBackground, modifier = Modifier.weight(1f)) { Text("Clear background") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onBackgroundWithLyrics, modifier = Modifier.weight(1f)) { Text("Background + lyrics") }
                        OutlinedButton(onClick = { onVideoControl("VIDEO_RESTART", true) }, modifier = Modifier.weight(1f)) { Text("Restart video") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { onVideoControl("VIDEO_PLAY", true) }, modifier = Modifier.weight(1f)) { Text("Play") }
                        OutlinedButton(onClick = { onVideoControl("VIDEO_PAUSE", false) }, modifier = Modifier.weight(1f)) { Text("Pause") }
                        OutlinedButton(onClick = { loopEnabled = !loopEnabled; onVideoControl("VIDEO_LOOP", loopEnabled) }, modifier = Modifier.weight(1f)) { Text(if (loopEnabled) "Loop on" else "Loop off") }
                        OutlinedButton(onClick = { muteEnabled = !muteEnabled; onVideoControl("VIDEO_MUTE", muteEnabled) }, modifier = Modifier.weight(1f)) { Text(if (muteEnabled) "Mute on" else "Mute off") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onClearLyrics, modifier = Modifier.weight(1f)) { Text("Clear lyrics") }
                        OutlinedButton(onClick = onClearAll, modifier = Modifier.weight(1f)) { Text("Clear all / blank") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onClearOverlay, modifier = Modifier.weight(1f)) { Text("Clear overlay dim") }
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isBlackScreenActive) onClearScreen() else onBlackScreen()
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.5.dp, if (isBlackScreenActive) GlowingCyan else VividBlue),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (isBlackScreenActive) RoyalBlueDark else Color.Transparent)
                        ) {
                            Text(if (isBlackScreenActive) "Show screen" else "Black screen", color = if (isBlackScreenActive) GlowingCyan else VividBlue)
                        }
                    }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- Live Slide Grid ---
        if (activeSong != null && activeSong.slides.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Slide Grid (Tap to Jump)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            val slides = activeSong.slides
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(slides) { idx, slide ->
                    val isSelected = idx == activeSlideIndex
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp)
                            .then(
                                if (isSelected) {
                                    Modifier.background(RoyalBlueGradient, RoundedCornerShape(10.dp))
                                } else {
                                    Modifier.border(1.dp, SpatialColors.InnerBorder, RoundedCornerShape(10.dp))
                                }
                            )
                            .clickable { onSelectSlide(idx) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color.Transparent else LightPanel
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Slide ${idx + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) GlowingCyan else TextMuted
                                )
                                if (isSelected) {
                                    Surface(
                                        color = GlowingCyan,
                                        shape = RoundedCornerShape(3.dp)
                                    ) {
                                        Text(
                                            text = "LIVE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RoyalBlueDark,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = slide.content,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = if (isSelected) PureWhite else TextDark,
                                maxLines = 3,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "No song selected. Go to Library or Create to add songs!",
                fontSize = 14.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        // --- Next / Prev Control Buttons ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPrevSlide() },
                enabled = isConnected && activeSong != null,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VividBlue, contentColor = PureWhite)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp), tint = GlowingCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Previous", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onNextSlide() },
                enabled = isConnected && activeSong != null,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VividBlue, contentColor = PureWhite)
            ) {
                Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp), tint = GlowingCyan)
            }
        }
    }
}

// ============================================================================
// TAB 2: SONG LIBRARY SCREEN
// ============================================================================

@Composable
fun SongLibraryScreen(
    songs: List<Song>,
    activeSong: Song?,
    onSelectSong: (Song) -> Unit,
    onEditSong: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onCreateNew: () -> Unit,
    onImportTxtFiles: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    (it.author?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Song Library",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = VividBlue
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onImportTxtFiles,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp), tint = VividBlue)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import TXT", fontWeight = FontWeight.Bold, color = VividBlue)
                }
                Button(
                    onClick = onCreateNew,
                    colors = ButtonDefaults.buttonColors(containerColor = VividBlue, contentColor = PureWhite),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = GlowingCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search songs...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No songs match \"$searchQuery\"" else "No songs in library yet.",
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onImportTxtFiles,
                        colors = ButtonDefaults.buttonColors(containerColor = VividBlue, contentColor = PureWhite),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp), tint = GlowingCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Hundreds of .TXT Songs", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredSongs, key = { it.id }) { song ->
                    val isActive = activeSong?.id == song.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isActive) 2.dp else 1.dp,
                                color = if (isActive) VividBlue else SpatialColors.InnerBorder,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = LightPanel)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                    Text(
                                        text = "${song.author ?: "Unknown Author"} • ${song.slides.size} slides",
                                        fontSize = 13.sp,
                                        color = TextMuted
                                    )
                                }
                                if (isActive) {
                                    Surface(
                                        color = RoyalBlueDark,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "LIVE",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GlowingCyan,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { onDeleteSong(song) },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                                OutlinedButton(
                                    onClick = { onEditSong(song) },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = VividBlue)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit", fontSize = 12.sp, color = VividBlue)
                                }
                                Button(
                                    onClick = { onSelectSong(song) },
                                    colors = ButtonDefaults.buttonColors(containerColor = VividBlue, contentColor = PureWhite)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = GlowingCyan)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Present", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper to convert font string to Compose FontFamily
fun getCustomFontFamily(fontFamilyStr: String): FontFamily {
    return when (fontFamilyStr.lowercase()) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "telugu", "telugu (sans)", "sans-serif-te" -> FontFamily(Typeface.create("sans-serif-te", Typeface.NORMAL))
        "telugu (serif)", "serif-te" -> FontFamily(Typeface.create("serif-te", Typeface.NORMAL))
        "hindi", "hindi (devanagari)", "hindi (sans)", "sans-serif-devanagari" -> FontFamily(
            Typeface.create("sans-serif-devanagari", Typeface.NORMAL))
        "hindi (serif)", "serif-devanagari" -> FontFamily(Typeface.create("serif-devanagari", Typeface.NORMAL))
        else -> FontFamily.SansSerif
    }
}

// ============================================================================
// TAB 3: FREESHOW STYLE SONG CREATOR & EDITOR
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeShowCreatorScreen(
    existingSong: Song?,
    mediaHost: String? = null,
    mediaPort: Int = 8766,
    receiverWidthDp: Int = 1920,
    availableTemplates: List<Template> = builtInTemplates(),
    defaultTemplate: Template? = null,
    onColorChange: (text: String?, background: String?) -> Unit = { _, _ -> },
    onMessage: (AppMessage) -> Unit = {},
    onSaveSong: (Song) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember(context) { PresenterPreferences(context) }

    var title by remember { mutableStateOf(existingSong?.title ?: "") }
    var author by remember { mutableStateOf(existingSong?.author ?: "") }
    var selectedTemplateId by remember(existingSong?.id) { mutableStateOf(existingSong?.displaySettings?.templateId ?: defaultTemplate?.id) }
    
    // Display Settings State
    var fontFamily by remember { mutableStateOf(existingSong?.displaySettings?.fontFamily ?: defaultTemplate?.fontFamily ?: "SansSerif") }
    var fontSize by remember { mutableStateOf(existingSong?.displaySettings?.fontSize?.toFloat() ?: defaultTemplate?.fontSize?.toFloat() ?: 48f) }
    var textColor by remember { mutableStateOf(existingSong?.displaySettings?.textColor ?: defaultTemplate?.textColor ?: "#00E5FF") }
    var backgroundType by remember { mutableStateOf(existingSong?.displaySettings?.backgroundType ?: defaultTemplate?.backgroundType?.name?.let { if (it == "SOLID") "COLOR" else it } ?: "COLOR") }
    var backgroundValue by remember { mutableStateOf(existingSong?.displaySettings?.backgroundValue ?: defaultTemplate?.backgroundColor ?: "#1E3A8A") }
    var horizontalAlignment by remember { mutableStateOf(existingSong?.displaySettings?.horizontalAlignment ?: defaultTemplate?.horizontalAlignment ?: "center") }
    var verticalAlignment by remember { mutableStateOf(existingSong?.displaySettings?.verticalAlignment ?: defaultTemplate?.verticalAlignment ?: "center") }
    var textBold by remember { mutableStateOf(existingSong?.displaySettings?.textBold ?: defaultTemplate?.bold ?: false) }
    var textItalic by remember { mutableStateOf(existingSong?.displaySettings?.textItalic ?: defaultTemplate?.italic ?: false) }
    var lineSpacing by remember { mutableFloatStateOf(existingSong?.displaySettings?.lineSpacing ?: defaultTemplate?.lineSpacing ?: 1.25f) }
    var paddingDp by remember { mutableFloatStateOf((existingSong?.displaySettings?.paddingDp ?: defaultTemplate?.paddingDp ?: 40).toFloat()) }
    var mediaFit by remember { mutableStateOf(existingSong?.displaySettings?.mediaFitMode ?: defaultTemplate?.mediaFitMode ?: MediaFitMode.FILL) }
    var overlayDimPercent by remember { mutableFloatStateOf(defaultTemplate?.overlayDimPercent?.toFloat() ?: ((255 - (existingSong?.displaySettings?.backgroundAlpha ?: 255)) * 100f / 255f)) }
    var shadowColorHex by remember { mutableStateOf(existingSong?.displaySettings?.shadowColor ?: defaultTemplate?.shadowColor) }
    var showColorPicker by remember { mutableStateOf(false) }
    var pickingBackgroundColor by remember { mutableStateOf(false) }
    var backgroundMediaId by remember { mutableStateOf(existingSong?.displaySettings?.backgroundMediaId ?: defaultTemplate?.backgroundMediaId) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    
    var selectedLocalFileName by remember { mutableStateOf<String?>(null) }

    fun saveColorPreferences() { coroutineScope.launch(Dispatchers.IO) { preferences.saveColors(textColor, backgroundValue) } }

    fun applyTemplate(template: Template) {
        selectedTemplateId = template.id
        fontFamily = template.fontFamily
        fontSize = template.fontSize.toFloat()
        textColor = template.textColor
        backgroundType = when (template.backgroundType) {
            TemplateBackgroundType.SOLID -> "COLOR"
            TemplateBackgroundType.IMAGE -> "IMAGE"
            TemplateBackgroundType.VIDEO -> "VIDEO"
            TemplateBackgroundType.NONE -> "NONE"
        }
        backgroundValue = template.backgroundColor
        horizontalAlignment = template.horizontalAlignment
        verticalAlignment = template.verticalAlignment
        textBold = template.bold
        textItalic = template.italic
        lineSpacing = template.lineSpacing
        paddingDp = (template.paddingDp + template.marginDp).toFloat()
        mediaFit = template.mediaFitMode
        overlayDimPercent = template.overlayDimPercent.toFloat()
        shadowColorHex = template.shadowColor
        backgroundMediaId = template.backgroundMediaId
        onColorChange(template.textColor, template.backgroundColor)
        saveColorPreferences()
    }

    LaunchedEffect(preferences) { preferences.colors.collect { (savedText, savedBackground) -> if (existingSong == null) { textColor = savedText; backgroundValue = savedBackground } } }

    // System Image Picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val host = mediaHost
            if (host == null) onMessage(AppMessages.connectionProblem())
            else coroutineScope.launch {
                uploadProgress = 0f
                runCatching { withContext(Dispatchers.IO) { MediaUploadClient.upload(context, it, host, mediaPort) { progress -> coroutineScope.launch { uploadProgress = progress } } } }
                    .onSuccess { uploaded -> backgroundMediaId = uploaded.mediaId; backgroundType = uploaded.type; backgroundValue = uploaded.url; selectedLocalFileName = "Uploaded photo"; onMessage(AppMessage(R.string.msg_media_ready, R.string.msg_media_ready_cause, severity = MessageSeverity.Success)) }
                    .onFailure { onMessage(AppMessages.mediaFailure(it.message.orEmpty())) }
            }
        }
    }

    // System Video Picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val host = mediaHost
            if (host == null) onMessage(AppMessages.connectionProblem())
            else coroutineScope.launch {
                uploadProgress = 0f
                runCatching { withContext(Dispatchers.IO) { MediaUploadClient.upload(context, it, host, mediaPort) { progress -> coroutineScope.launch { uploadProgress = progress } } } }
                    .onSuccess { uploaded -> backgroundMediaId = uploaded.mediaId; backgroundType = uploaded.type; backgroundValue = uploaded.url; selectedLocalFileName = "Uploaded video"; onMessage(AppMessage(R.string.msg_media_ready, R.string.msg_media_ready_cause, severity = MessageSeverity.Success)) }
                    .onFailure { onMessage(AppMessages.mediaFailure(it.message.orEmpty())) }
            }
        }
    }

    val initialLyrics = remember(existingSong) {
        if (existingSong != null) {
            existingSong.sections.joinToString("\n\n") { section ->
                "[${section.label}]\n" + section.slides.joinToString("\n\n") { it.content }
            }
        } else ""
    }
    
    var lyricsText by remember { mutableStateOf(initialLyrics) }

    if (uploadProgress in 0f..0.999f) LinearProgressIndicator(progress = uploadProgress, modifier = Modifier.fillMaxWidth())
    
    val parsedSections = remember(lyricsText) {
        parseLyricsToSections(lyricsText)
    }
    val parsedSlides = remember(parsedSections) {
        parsedSections.flatMap { section ->
            section.slides.map { slide -> Pair(section.label, slide) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(
                if (existingSong != null) Icons.Default.Edit else Icons.Default.Create,
                contentDescription = null,
                tint = VividBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (existingSong != null) "Edit Song" else "Create New Song",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = VividBlue
            )
        }

        Text("Start with a template", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            availableTemplates.forEach { template ->
                FilterChip(
                    selected = selectedTemplateId == template.id,
                    onClick = { applyTemplate(template) },
                    label = { Text(template.name, maxLines = 1) }
                )
            }
        }

        // Title Input
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Song Title") },
            placeholder = { Text("e.g. How Great Is Our God") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )

        // Author Input
        OutlinedTextField(
            value = author,
            onValueChange = { author = it },
            label = { Text("Author / Artist (Optional)") },
            placeholder = { Text("e.g. Chris Tomlin") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        // Quick Insert Section Tags
        Text(
            text = "Insert Section Tag:",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("[Verse 1]", "[Chorus]", "[Bridge]", "[Intro]", "[Outro]").forEach { tag ->
                SuggestionChip(
                    onClick = {
                        lyricsText = if (lyricsText.isBlank()) "$tag\n" else "$lyricsText\n\n$tag\n"
                    },
                    label = { Text(tag, fontSize = 12.sp, color = VividBlue) }
                )
            }
        }

        // Lyrics Area
        OutlinedTextField(
            value = lyricsText,
            onValueChange = { lyricsText = it },
            label = { Text("Paste or Type Song Lyrics") },
            placeholder = {
                Text(
                    "[Verse 1]\nAmazing grace, how sweet the sound\nThat saved a wretch like me\n\n[Chorus]\nMy chains are gone, I've been set free",
                    color = TextMuted
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(bottom = 16.dp)
        )

        // ====================================================================
        // SECTION 1: TYPOGRAPHY & FONT SETTINGS
        // ====================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .border(1.dp, SpatialColors.InnerBorder, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = LightPanel),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TextFields, contentDescription = null, tint = VividBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Typography & Words Style", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                // Font selection with English, Telugu, and Hindi options
                Text("Font Style & Script", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                
                // English Fonts
                Text("English", fontSize = 11.sp, color = VividBlue, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("SansSerif", "Serif", "Monospace", "Cursive").forEach { font ->
                        FilterChip(
                            selected = fontFamily == font,
                            onClick = { fontFamily = font },
                            label = { Text(font, fontSize = 12.sp) }
                        )
                    }
                }

                // Telugu Fonts
                Text("Telugu (తెలుగు)", fontSize = 11.sp, color = VividBlue, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Telugu", "Telugu (Serif)").forEach { font ->
                        FilterChip(
                            selected = fontFamily == font,
                            onClick = { fontFamily = font },
                            label = { Text(if (font == "Telugu") "Telugu (తెలుగు)" else "Telugu Serif", fontSize = 12.sp) }
                        )
                    }
                }

                // Hindi Fonts
                Text("Hindi / Devanagari (हिन्दी)", fontSize = 11.sp, color = VividBlue, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Hindi", "Hindi (Serif)").forEach { font ->
                        FilterChip(
                            selected = fontFamily == font,
                            onClick = { fontFamily = font },
                            label = { Text(if (font == "Hindi") "Hindi (हिन्दी)" else "Hindi Serif", fontSize = 12.sp) }
                        )
                    }
                }

                // Live 16:9 preview uses the same typography, alignment, padding and fit as TV output.
                Text("Live TV Preview (16:9)", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                val previewText = parsedSlides.firstOrNull()?.second?.content ?: when {
                    fontFamily.startsWith("Telugu") -> "యేసే నా మార్గము సత్యము జీవము"
                    fontFamily.startsWith("Hindi") -> "येशु ही मार्ग सत्य और जीवन है"
                    else -> "Amazing grace, how sweet the sound"
                }
                LyricsSlidePreview(
                    text = previewText,
                    fontFamilyName = fontFamily,
                    fontSizeSp = fontSize,
                    textColorHex = textColor,
                    backgroundColorHex = backgroundValue.takeIf { it.startsWith("#") } ?: "#000000",
                    receiverWidthDp = receiverWidthDp,
                    horizontalAlignment = horizontalAlignment,
                    verticalAlignment = verticalAlignment,
                    bold = textBold,
                    italic = textItalic,
                    lineSpacing = lineSpacing,
                    paddingDp = paddingDp.toInt(),
                    backgroundType = backgroundType,
                    backgroundUrl = backgroundValue.takeIf { it.startsWith("http://") || it.startsWith("https://") },
                    mediaFit = mediaFit.name,
                    overlayDimPercent = overlayDimPercent.toInt(),
                    shadowColorHex = shadowColorHex,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Text Size
                Text("Text Size: ${fontSize.toInt()} sp", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 16f..160f,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Text weight and style", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = textBold, onClick = { textBold = !textBold }, label = { Text("Bold") })
                    FilterChip(selected = textItalic, onClick = { textItalic = !textItalic }, label = { Text("Italic") })
                }
                Text("Horizontal alignment", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("left", "center", "right").forEach { alignment ->
                        FilterChip(selected = horizontalAlignment == alignment, onClick = { horizontalAlignment = alignment }, label = { Text(alignment.replaceFirstChar { it.uppercase() }, maxLines = 1) })
                    }
                }
                Text("Vertical alignment", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("top", "center", "bottom").forEach { alignment ->
                        FilterChip(selected = verticalAlignment == alignment, onClick = { verticalAlignment = alignment }, label = { Text(alignment.replaceFirstChar { it.uppercase() }, maxLines = 1) })
                    }
                }
                Text("Line spacing: ${String.format("%.2f", lineSpacing)}", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Slider(value = lineSpacing, onValueChange = { lineSpacing = it }, valueRange = 0.8f..2.5f)
                Text("Text safe-area padding: ${paddingDp.toInt()} dp", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Slider(value = paddingDp, onValueChange = { paddingDp = it }, valueRange = 0f..200f)
                Text("Background fit mode", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(MediaFitMode.FILL, MediaFitMode.FIT, MediaFitMode.STRETCH).forEach { fit ->
                        FilterChip(selected = mediaFit == fit, onClick = { mediaFit = fit }, label = { Text(fit.name) })
                    }
                }
                Text("Background dim: ${overlayDimPercent.toInt()}%", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Slider(value = overlayDimPercent, onValueChange = { overlayDimPercent = it }, valueRange = 0f..100f)
                
                Spacer(modifier = Modifier.height(8.dp))

                // Text Color
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Text Color (Words)", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { pickingBackgroundColor = false; showColorPicker = true }) { Text("Custom color…") }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val textColors = listOf("#00E5FF", "#38BDF8", "#FFFFFF", "#FFFF00", "#000000")
                    textColors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(android.graphics.Color.parseColor(hex)), RoundedCornerShape(18.dp))
                                .border(2.dp, if (textColor == hex) VividBlue else Color.LightGray, RoundedCornerShape(18.dp))
                                .clickable { textColor = hex; onColorChange(hex, null); saveColorPreferences() }
                        )
                    }
                }
            }
        }

        // ====================================================================
        // SECTION 2: DEDICATED BACKGROUND & MEDIA STUDIO
        // ====================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .border(1.5.dp, VividBlue, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = LightPanel),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wallpaper, contentDescription = null, tint = VividBlue, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Background & Media Studio", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    }
                    Surface(
                        color = VividBlue,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = backgroundType,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Selector Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("COLOR", "IMAGE", "VIDEO").forEach { type ->
                        val isSelected = backgroundType == type
                        Button(
                            onClick = {
                                backgroundType = type
                                if (type == "COLOR" && !backgroundValue.startsWith("#")) {
                                    backgroundValue = "#1E3A8A"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) VividBlue else LightPanelElevated,
                                contentColor = if (isSelected) PureWhite else TextDark
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) VividBlue else SpatialColors.InnerBorder)
                        ) {
                            Text(type, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (backgroundType) {
                    "COLOR" -> {
                        Text("Choose Solid or Gradient Palette:", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val bgColors = listOf("#1E3A8A", "#2563EB", "#0F172A", "#000000", "#FFFFFF")
                            bgColors.forEach { hex ->
                                val isSelected = backgroundValue == hex
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .background(Color(android.graphics.Color.parseColor(hex)), RoundedCornerShape(21.dp))
                                            .border(2.dp, if (isSelected) VividBlue else Color.LightGray, RoundedCornerShape(21.dp))
                                            .clickable { backgroundValue = hex; onColorChange(null, hex); saveColorPreferences() }
                                    )
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = VividBlue, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { pickingBackgroundColor = true; showColorPicker = true }) { Text("Custom background color…") }
                    }
                    "IMAGE" -> {
                        Text("Select Image from Local Storage:", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VividBlue, contentColor = PureWhite),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = GlowingCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose Local Photo / Media", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        // Local Image Thumbnail Preview
                        if (backgroundValue.startsWith("data:image") || backgroundValue.startsWith("content://") || backgroundValue.startsWith("file://")) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .border(1.dp, VividBlue, RoundedCornerShape(8.dp)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = backgroundValue,
                                        contentDescription = "Selected Local Background",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        color = RoyalBlueDark.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = selectedLocalFileName ?: "Local Image Ready for TV",
                                                color = GlowingCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Transmitted Live",
                                                color = PureWhite,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "VIDEO" -> {
                        Text("Select Video from Local Storage or Presets:", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VividBlue, contentColor = PureWhite),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = GlowingCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose Local Video File", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        if (selectedLocalFileName != null && backgroundType == "VIDEO") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = LightPanelElevated,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, SpatialColors.InnerBorder)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Movie, contentDescription = null, tint = VividBlue)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(selectedLocalFileName.orEmpty(), fontSize = 12.sp, color = TextDark, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Or Choose Motion Preset:", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        val presets = listOf(
                            Pair("🌌 Worship Particles", "#1E3A8A"),
                            Pair("💡 Stage Rays", "#0284C7"),
                            Pair("🌊 Blue Waves", "#0F172A"),
                            Pair("⭐ Deep Space", "#000000")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.forEach { (label, hex) ->
                                SuggestionChip(
                                    onClick = {
                                        backgroundValue = hex
                                    },
                                    label = { Text(label, fontSize = 11.sp, color = VividBlue) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Live Slide Cards Preview ---
        Text(
            text = "Slide Preview (${parsedSlides.size} slides generated)",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = VividBlue,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (parsedSlides.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = LightPanel)
            ) {
                Text(
                    text = "Type or paste lyrics above to preview slides automatically!",
                    fontSize = 13.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                parsedSlides.forEachIndexed { idx, (sectionLabel, slide) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SpatialColors.InnerBorder, RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = LightPanel)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    color = RoyalBlueDark,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = sectionLabel.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GlowingCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text("Slide #${idx + 1}", fontSize = 11.sp, color = TextMuted)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LyricsSlidePreview(
                                text = slide.content,
                                fontFamilyName = fontFamily,
                                fontSizeSp = fontSize,
                                textColorHex = textColor,
                                backgroundColorHex = backgroundValue.takeIf { it.startsWith("#") } ?: "#000000",
                                receiverWidthDp = receiverWidthDp,
                                horizontalAlignment = horizontalAlignment,
                                verticalAlignment = verticalAlignment,
                                bold = textBold,
                                italic = textItalic,
                                lineSpacing = lineSpacing,
                                paddingDp = paddingDp.toInt(),
                                backgroundType = backgroundType,
                                backgroundUrl = backgroundValue.takeIf { it.startsWith("http://") || it.startsWith("https://") },
                                mediaFit = mediaFit.name,
                                shadowColorHex = shadowColorHex,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Save Button
        Button(
            onClick = {
                val newSong = Song(
                    id = existingSong?.id ?: UUID.randomUUID().toString(),
                    title = title.ifBlank { "Untitled Song" },
                    author = author.ifBlank { null },
                    sections = parsedSections,
                    displaySettings = DisplaySettings(
                        fontFamily = fontFamily,
                        fontSize = fontSize.toInt(),
                        textColor = textColor,
                        backgroundType = backgroundType,
                        backgroundValue = backgroundValue,
                        backgroundMediaId = backgroundMediaId,
                        textBold = textBold,
                        textItalic = textItalic,
                        horizontalAlignment = horizontalAlignment,
                        verticalAlignment = verticalAlignment,
                        lineSpacing = lineSpacing,
                        shadowColor = shadowColorHex,
                        mediaFitMode = mediaFit,
                        templateId = selectedTemplateId,
                        backgroundAlpha = (255 - (overlayDimPercent.toInt().coerceIn(0, 100) * 255 / 100)),
                        paddingDp = paddingDp.toInt()
                    )
                )
                onSaveSong(newSong)
            },
            enabled = title.isNotBlank() && lyricsText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VividBlue, contentColor = PureWhite),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp), tint = GlowingCyan)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Song & Present", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (showColorPicker) {
        val initialColor = runCatching {
            android.graphics.Color.parseColor(if (pickingBackgroundColor) backgroundValue else textColor)
        }.getOrDefault(android.graphics.Color.WHITE)
        ColorPickerDialog(
            initialColor = initialColor,
            onDismiss = { showColorPicker = false },
            onApply = { selected ->
                val value = String.format("#%08X", selected)
                if (pickingBackgroundColor) { backgroundValue = value; onColorChange(null, value) }
                else { textColor = value; onColorChange(value, null) }
                saveColorPreferences()
                showColorPicker = false
            }
        )
    }
}
