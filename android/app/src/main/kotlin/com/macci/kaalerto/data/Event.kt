package com.macci.kaalerto.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One immutable observation. [id] is a content hash (or, for seed fixtures, the
 * fixture's own id) — inserting the same id twice is a no-op, which is what makes
 * re-delivery of the same event over server, mesh and SMS harmless.
 *
 * The local store is a replica of this table, not a cache: rows are never updated or
 * deleted except TTL expiry (CLAUDE.md architecture summary).
 */
@Entity(tableName = "events")
data class Event(
    @PrimaryKey val id: String,
    val type: String,
    val lat: Double,
    val lon: Double,
    val featureRef: String?,
    val severity: String?,
    val waterLevel: String?,
    val authorId: String,
    /** Embedded at creation, never looked up — a receiving device renders this offline. */
    val authorName: String,
    val authorRole: String,
    val timestampMs: Long,
    val expiresAt: Long,
    val origin: String,
    val hopCount: Int,
    val note: String?,
    /** Only set on `type = "dispute"` events: cleared_now | worse | shallower | wrong_location. */
    val disputeReason: String? = null,
)
