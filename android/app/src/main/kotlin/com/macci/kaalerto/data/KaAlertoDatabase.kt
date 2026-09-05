package com.macci.kaalerto.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Version 3 adds Event.payload for the day 8 SOS events.
@Database(entities = [Event::class, FeatureState::class], version = 3, exportSchema = false)
abstract class KaAlertoDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun featureStateDao(): FeatureStateDao

    companion object {
        @Volatile private var instance: KaAlertoDatabase? = null

        fun getInstance(context: Context): KaAlertoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KaAlertoDatabase::class.java,
                    "kaalerto.db",
                )
                    // No migrations written yet — acceptable pre-release, since local
                    // data is a replica that reseeds itself, never the source of truth.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
    }
}
