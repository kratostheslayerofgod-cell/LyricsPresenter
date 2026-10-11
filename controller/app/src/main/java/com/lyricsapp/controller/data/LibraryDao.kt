package com.lyricsapp.controller.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    fun observeSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSong(id: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSong(song: SongEntity)

    @Delete
    suspend fun deleteSong(song: SongEntity)

    @Query("SELECT * FROM setlists ORDER BY updatedAt DESC")
    fun observeSetlists(): Flow<List<SetlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetlist(setlist: SetlistEntity)

    @Delete
    suspend fun deleteSetlist(setlist: SetlistEntity)
}
