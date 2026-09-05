package com.macci.kaalerto.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Event::class, FeatureState::class], version = 1, exportSchema = false)
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
                ).build().also { instance = it }
            }
    }
}
