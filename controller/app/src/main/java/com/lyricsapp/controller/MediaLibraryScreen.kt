package com.lyricsapp.controller

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lyricsapp.shared.MediaAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MediaLibraryScreen(
    assets: List<MediaAsset>,
    mediaHost: String?,
    mediaPort: Int,
    onImported: (MediaAsset) -> Unit,
    onMessage: (AppMessage) -> Unit,
    onSetBackground: (MediaAsset, showLyrics: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var selected by remember { mutableStateOf<MediaAsset?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && mediaHost != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            scope.launch {
                uploadProgress = 0.001f
                runCatching { withContext(Dispatchers.IO) { MediaUploadClient.upload(context, uri, mediaHost, mediaPort) { p -> scope.launch { uploadProgress = p } } } }
                    .onSuccess { result ->
                        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Media"
                        onImported(MediaAsset(result.mediaId, name, result.type, uri.toString()))
                        uploadProgress = 0f
                    }
                    .onFailure { onMessage(AppMessages.mediaFailure(it.message.orEmpty())); uploadProgress = 0f }
            }
        } else if (uri != null) onMessage(AppMessages.connectionProblem())
    }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Media", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Button(onClick = { picker.launch(arrayOf("image/*", "video/*")) }) { Text("Import") }
        }
        if (uploadProgress > 0f) LinearProgressIndicator(progress = uploadProgress, modifier = Modifier.fillMaxWidth())
        LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(assets, key = { it.mediaId }) { asset ->
                Card(Modifier.clickable { selected = asset }) {
                    Box(Modifier.fillMaxWidth().height(110.dp).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                        if (asset.type == "IMAGE" && asset.sourceUri != null) AsyncImage(model = Uri.parse(asset.sourceUri), contentDescription = asset.name, modifier = Modifier.fillMaxSize())
                        else Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                    Text(asset.name, Modifier.padding(8.dp), maxLines = 1, fontSize = 12.sp)
                }
            }
        }
        selected?.let { asset ->
            Text(asset.name, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSetBackground(asset, false) }, modifier = Modifier.weight(1f)) { Text("Background only") }
                Button(onClick = { onSetBackground(asset, true) }, modifier = Modifier.weight(1f)) { Text("With lyrics") }
            }
        }
    }
}
