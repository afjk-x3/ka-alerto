package com.macci.kaalerto.data

import kotlinx.coroutines.flow.Flow

/**
 * Dedup is [EventDao]'s `OnConflictStrategy.IGNORE` on the content-hash primary key —
 * that's the whole story: re-delivery of the same event over multiple transports is
 * harmless (CLAUDE.md architecture summary).
 */
class EventRepository(private val eventDao: EventDao) {
    fun observeAll(): Flow<List<Event>> = eventDao.observeAll()

    suspend fun insert(events: List<Event>) = eventDao.insertAll(events)

    suspend fun insert(event: Event) = eventDao.insert(event)

    suspend fun isEmpty(): Boolean = eventDao.count() == 0
}
