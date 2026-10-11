package com.lyricsapp.controller.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lyricsapp.shared.SectionType
import com.lyricsapp.shared.DisplaySettings
import com.lyricsapp.shared.Song
import com.lyricsapp.shared.SongSection
import com.lyricsapp.shared.SongSlide

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val sections: List<SongSection>,
    val displaySettings: DisplaySettings,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toModel() = Song(id, title, author, sections, displaySettings, createdAt, updatedAt)

    companion object {
        fun fromModel(song: Song) = SongEntity(
            id = song.id,
            title = song.title,
            author = song.author,
            sections = song.sections,
            displaySettings = song.displaySettings,
            createdAt = song.createdAt,
            updatedAt = song.updatedAt
        )
    }
}

@Entity(tableName = "setlists")
data class SetlistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val songIds: List<String>,
    val updatedAt: Long
)
