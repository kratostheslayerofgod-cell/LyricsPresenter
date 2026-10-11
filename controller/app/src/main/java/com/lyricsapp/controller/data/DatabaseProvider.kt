package com.lyricsapp.controller.data

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile
    private var instance: LyricsDatabase? = null

    fun get(context: Context): LyricsDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LyricsDatabase::class.java,
                "lyrics-presenter.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
}
