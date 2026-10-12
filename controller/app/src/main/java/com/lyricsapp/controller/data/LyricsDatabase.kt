package com.lyricsapp.controller.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [SongEntity::class, SetlistEntity::class, SongFolderEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}
