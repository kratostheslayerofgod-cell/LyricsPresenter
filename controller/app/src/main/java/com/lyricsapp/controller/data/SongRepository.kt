package com.lyricsapp.controller.data

import androidx.room.withTransaction
import com.lyricsapp.shared.Setlist
import com.lyricsapp.shared.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SongRepository(private val database: LyricsDatabase) {
    private val dao = database.libraryDao()

    fun observeSongs(): Flow<List<Song>> = dao.observeSongs().map { songs ->
        songs.map(SongEntity::toModel)
    }

    fun observeSetlists(): Flow<List<Setlist>> = dao.observeSetlists().map { setlists ->
        setlists.map { Setlist(it.id, it.name, it.songIds, it.updatedAt) }
    }

    fun observeFolders(): Flow<List<SongFolder>> = dao.observeFolders().map { folders ->
        folders.map { SongFolder(it.id, it.name, it.createdAt) }
    }

    suspend fun getSong(id: String): Song? = dao.getSong(id)?.toModel()

    suspend fun saveSong(song: Song) {
        database.withTransaction {
            dao.saveSong(SongEntity.fromModel(song))
        }
    }

    suspend fun deleteSong(song: Song) {
        dao.deleteSong(SongEntity.fromModel(song))
    }

    suspend fun createFolder(name: String) {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Folder name cannot be blank" }
        dao.saveFolder(SongFolderEntity(java.util.UUID.randomUUID().toString(), normalized, System.currentTimeMillis()))
    }

    suspend fun renameFolder(folder: SongFolder, name: String) {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Folder name cannot be blank" }
        dao.saveFolder(folder.copy(name = normalized).toEntity())
    }

    suspend fun deleteFolder(folder: SongFolder) {
        database.withTransaction {
            dao.moveFolderSongsToUnfiled(folder.id)
            dao.deleteFolder(folder.id)
        }
    }

    suspend fun moveSongToFolder(song: Song, folderId: String?) {
        dao.moveSong(song.id, folderId)
    }

    suspend fun saveSetlist(setlist: Setlist) {
        dao.saveSetlist(SetlistEntity(setlist.id, setlist.name, setlist.songIds, setlist.updatedAt))
    }

    suspend fun deleteSetlist(setlist: Setlist) {
        dao.deleteSetlist(SetlistEntity(setlist.id, setlist.name, setlist.songIds, setlist.updatedAt))
    }
}

private fun SongFolder.toEntity() = SongFolderEntity(id, name, createdAt)
