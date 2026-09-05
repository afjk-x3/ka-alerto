package com.macci.kaalerto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Populated by the day 4 reducer via [com.macci.kaalerto.map.MapViewModel] — see the class doc on [FeatureState]. */
@Dao
interface FeatureStateDao {
    // REPLACE, not IGNORE. The append-only rule is about the `events` table; this one is
    // the *materialized fold* of it, keyed on featureRef, and it is rewritten every time
    // the reducer runs. IGNORE would pin each feature to whatever severity, confidence
    // and bucket it happened to have the first time it was seen, and silently drop every
    // later recomputation — a road that rose S1 -> S3 would stay S1 in this table forever.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: FeatureState)

    @Query("SELECT * FROM feature_state")
    fun observeAll(): Flow<List<FeatureState>>
}
