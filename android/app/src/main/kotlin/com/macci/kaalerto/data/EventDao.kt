package com.macci.kaalerto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    @Query("DELETE FROM events WHERE origin = 'seed'")
    suspend fun deleteSeeds()

    /**
     * Swaps the seed fixtures for fresh copies (SeedLoader). One transaction, so the
     * event Flow emits once — observers see the old seeds or the new ones, never an
     * empty map in between. Rows of any other origin are untouched.
     */
    @Transaction
    suspend fun replaceSeeds(seeds: List<Event>) {
        deleteSeeds()
        insertAll(seeds)
    }
}
