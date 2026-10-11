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

private val Context.presenterStore by preferencesDataStore("presenter_preferences")

class PresenterPreferences(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val textColorKey = stringPreferencesKey("last_text_color")
    private val backgroundColorKey = stringPreferencesKey("last_background_color")
    private val templatesKey = stringPreferencesKey("custom_templates")
    private val mediaKey = stringPreferencesKey("media_assets")

    val colors: Flow<Pair<String, String>> = context.presenterStore.data.map { prefs ->
        (prefs[textColorKey] ?: "#FFFFFFFF") to (prefs[backgroundColorKey] ?: "#FF000000")
    }
    val templates: Flow<List<Template>> = context.presenterStore.data.map { prefs ->
        runCatching { json.decodeFromString<List<Template>>(prefs[templatesKey].orEmpty()) }.getOrDefault(emptyList())
    }
    val media: Flow<List<MediaAsset>> = context.presenterStore.data.map { prefs ->
        runCatching { json.decodeFromString<List<MediaAsset>>(prefs[mediaKey].orEmpty()) }.getOrDefault(emptyList())
    }
    suspend fun saveColors(text: String, background: String) { context.presenterStore.edit { it[textColorKey] = text; it[backgroundColorKey] = background } }
    suspend fun saveTemplates(items: List<Template>) { context.presenterStore.edit { it[templatesKey] = json.encodeToString(items) } }
    suspend fun saveMedia(items: List<MediaAsset>) { context.presenterStore.edit { it[mediaKey] = json.encodeToString(items) } }
}
