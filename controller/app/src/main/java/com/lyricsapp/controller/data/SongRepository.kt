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

    suspend fun getSong(id: String): Song? = dao.getSong(id)?.toModel()

    suspend fun saveSong(song: Song) {
        database.withTransaction {
            dao.saveSong(SongEntity.fromModel(song))
        }
    }

    suspend fun deleteSong(song: Song) {
        dao.deleteSong(SongEntity.fromModel(song))
    }

    suspend fun saveSetlist(setlist: Setlist) {
        dao.saveSetlist(SetlistEntity(setlist.id, setlist.name, setlist.songIds, setlist.updatedAt))
    }

    suspend fun deleteSetlist(setlist: Setlist) {
        dao.deleteSetlist(SetlistEntity(setlist.id, setlist.name, setlist.songIds, setlist.updatedAt))
    }
}
