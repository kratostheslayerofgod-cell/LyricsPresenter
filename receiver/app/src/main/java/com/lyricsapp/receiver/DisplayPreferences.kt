package com.lyricsapp.receiver

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.displayPreferences by preferencesDataStore("display_preferences")

class DisplayPreferences(private val context: Context) {
    private val text = stringPreferencesKey("last_text_color")
    private val background = stringPreferencesKey("last_background_color")
    suspend fun restore(): Pair<String, String> {
        val preferences = context.displayPreferences.data.first()
        return (preferences[text] ?: "#FFFFFFFF") to (preferences[background] ?: "#FF000000")
    }
    suspend fun save(textColor: String, backgroundColor: String) {
        context.displayPreferences.edit { it[text] = textColor; it[background] = backgroundColor }
    }
}
