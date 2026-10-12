package com.lyricsapp.controller.data

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Room

object DatabaseProvider {
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE songs ADD COLUMN folderId TEXT")
            database.execSQL("CREATE TABLE IF NOT EXISTS song_folders (id TEXT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
        }
    }

    @Volatile
    private var instance: LyricsDatabase? = null

    fun get(context: Context): LyricsDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LyricsDatabase::class.java,
                "lyrics-presenter.db"
            ).addMigrations(MIGRATION_2_3).fallbackToDestructiveMigration().build().also { instance = it }
        }
}
