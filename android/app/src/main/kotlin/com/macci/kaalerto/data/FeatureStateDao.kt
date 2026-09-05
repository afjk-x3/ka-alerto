package com.macci.kaalerto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Unused until the day 4 reducer lands — see the class doc on [FeatureState]. */
@Dao
interface FeatureStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: FeatureState)

    @Query("SELECT * FROM feature_state")
    fun observeAll(): Flow<List<FeatureState>>
}
