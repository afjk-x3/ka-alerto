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

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int
}
