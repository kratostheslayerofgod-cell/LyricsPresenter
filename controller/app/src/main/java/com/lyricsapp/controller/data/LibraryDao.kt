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

    @Query("UPDATE songs SET folderId = :folderId WHERE id = :songId")
    suspend fun moveSong(songId: String, folderId: String?)

    @Query("UPDATE songs SET folderId = NULL WHERE folderId = :folderId")
    suspend fun moveFolderSongsToUnfiled(folderId: String)

    @Query("SELECT * FROM song_folders ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<SongFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFolder(folder: SongFolderEntity)

    @Query("DELETE FROM song_folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: String)

    @Query("SELECT * FROM setlists ORDER BY updatedAt DESC")
    fun observeSetlists(): Flow<List<SetlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetlist(setlist: SetlistEntity)

    @Delete
    suspend fun deleteSetlist(setlist: SetlistEntity)
}
