package com.macci.kaalerto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    // IGNORE on the content-hash primary key is the entire dedup story: re-delivery of
    // the same event over server, mesh and SMS collapses to one row for free.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: Event)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<Event>)

    @Query("SELECT * FROM events ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<Event>>

    // One-shot reads for the mesh exchange (mesh/MeshService.kt). It needs a snapshot at
    // a specific instant — "what do I hold right now, that this peer does not" — which
    // is a different question from observeAll()'s continuous one, and collecting a Flow
    // for a single answer would leave the diff racing the next emission.
    @Query("SELECT id FROM events")
    suspend fun allIds(): List<String>

    @Query("SELECT * FROM events")
    suspend fun all(): List<Event>

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    /**
     * Purges events that expired long enough ago to be worth forgetting.
     *
     * The cutoff is **not** `expiresAt < now`, and that distinction is load-bearing.
     * The reducer derives `isStale` as `now > max(expiresAt)`, so a feature can only
     * render as the grey "Luma na — kailangang tingnan" marker the map legend
     * advertises *while its expired events still exist*. Deleting at the instant of
     * expiry means that after any cold start a stale road silently disappears from the
     * map instead of prompting someone to go and check it — which is the opposite of
     * what expiry is for. Callers pass `now - RETENTION_AFTER_EXPIRY_MS`.
     */
    @Query("DELETE FROM events WHERE expiresAt < :cutoffMs")
    suspend fun deleteExpiredBefore(cutoffMs: Long)
}
