package com.lyricsapp.controller.data

import androidx.room.TypeConverter
import com.lyricsapp.shared.SongSection
import com.lyricsapp.shared.DisplaySettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json

    @TypeConverter
    fun sectionsToJson(value: List<SongSection>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToSections(value: String): List<SongSection> = json.decodeFromString(value)

    @TypeConverter
    fun displaySettingsToJson(value: DisplaySettings): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToDisplaySettings(value: String): DisplaySettings = json.decodeFromString(value)

    @TypeConverter
    fun songIdsToJson(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToSongIds(value: String): List<String> = json.decodeFromString(value)
}
