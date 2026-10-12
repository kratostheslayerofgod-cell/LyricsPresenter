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
    val updatedAt: Long,
    val folderId: String? = null
) {
    fun toModel() = Song(id, title, author, sections, displaySettings, createdAt, updatedAt, folderId)

    companion object {
        fun fromModel(song: Song) = SongEntity(
            id = song.id,
            title = song.title,
            author = song.author,
            sections = song.sections,
            displaySettings = song.displaySettings,
            createdAt = song.createdAt,
            updatedAt = song.updatedAt,
            folderId = song.folderId
        )
    }
}

@Entity(tableName = "song_folders")
data class SongFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
)

data class SongFolder(
    val id: String,
    val name: String,
    val createdAt: Long
)

@Entity(tableName = "setlists")
data class SetlistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val songIds: List<String>,
    val updatedAt: Long
)
