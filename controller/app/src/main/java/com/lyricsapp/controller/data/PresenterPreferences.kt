package com.lyricsapp.controller.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lyricsapp.shared.Template
import com.lyricsapp.shared.MediaAsset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ReceiverEndpoint(val host: String, val port: Int, val name: String)

private val Context.presenterStore by preferencesDataStore("presenter_preferences")

class PresenterPreferences(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val textColorKey = stringPreferencesKey("last_text_color")
    private val backgroundColorKey = stringPreferencesKey("last_background_color")
    private val templatesKey = stringPreferencesKey("custom_templates")
    private val mediaKey = stringPreferencesKey("media_assets")
    private val receiverHostKey = stringPreferencesKey("last_receiver_host")
    private val receiverPortKey = androidx.datastore.preferences.core.intPreferencesKey("last_receiver_port")
    private val receiverNameKey = stringPreferencesKey("last_receiver_name")

    val colors: Flow<Pair<String, String>> = context.presenterStore.data.map { prefs ->
        (prefs[textColorKey] ?: "#FFFFFFFF") to (prefs[backgroundColorKey] ?: "#FF000000")
    }
    val templates: Flow<List<Template>> = context.presenterStore.data.map { prefs ->
        runCatching { json.decodeFromString<List<Template>>(prefs[templatesKey].orEmpty()) }.getOrDefault(emptyList())
    }
    val media: Flow<List<MediaAsset>> = context.presenterStore.data.map { prefs ->
        runCatching { json.decodeFromString<List<MediaAsset>>(prefs[mediaKey].orEmpty()) }.getOrDefault(emptyList())
    }
    val lastReceiver: Flow<ReceiverEndpoint?> = context.presenterStore.data.map { prefs ->
        prefs[receiverHostKey]?.takeIf { it.isNotBlank() }?.let { host ->
            ReceiverEndpoint(host, prefs[receiverPortKey] ?: 8765, prefs[receiverNameKey] ?: "TV Receiver")
        }
    }
    suspend fun saveReceiver(host: String, port: Int, name: String) {
        context.presenterStore.edit { prefs -> prefs[receiverHostKey] = host; prefs[receiverPortKey] = port; prefs[receiverNameKey] = name }
    }
    suspend fun saveColors(text: String, background: String) { context.presenterStore.edit { it[textColorKey] = text; it[backgroundColorKey] = background } }
    suspend fun saveTemplates(items: List<Template>) { context.presenterStore.edit { it[templatesKey] = json.encodeToString(items) } }
    suspend fun saveMedia(items: List<MediaAsset>) { context.presenterStore.edit { it[mediaKey] = json.encodeToString(items) } }
}
