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

    /** Point-in-time snapshots for the mesh anti-entropy exchange — see [EventDao.allIds]. */
    suspend fun allIds(): List<String> = eventDao.allIds()

    suspend fun all(): List<Event> = eventDao.all()

    suspend fun isEmpty(): Boolean = eventDao.count() == 0

    /**
     * Purge long-expired events. Safe to call on every cold start.
     *
     * [RETENTION_AFTER_EXPIRY_MS] is the grace between an event expiring and being
     * forgotten. It exists so an expired report still renders as stale for a while
     * rather than vanishing — see [EventDao.deleteExpiredBefore].
     */
    suspend fun deleteExpired(nowMs: Long = System.currentTimeMillis()) =
        eventDao.deleteExpiredBefore(nowMs - RETENTION_AFTER_EXPIRY_MS)

    companion object {
        /**
         * 24 h. Long enough that a stale marker is visible for a full day after the
         * report lapsed — the window in which "go and check this" is still useful
         * advice — and short enough to bound both storage and the mesh diff.
         */
        const val RETENTION_AFTER_EXPIRY_MS = 24L * 60 * 60 * 1000
    }
}
