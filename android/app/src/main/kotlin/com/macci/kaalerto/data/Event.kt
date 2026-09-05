package com.macci.kaalerto.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One immutable observation. [id] is a content hash (or, for seed fixtures, the
 * fixture's own id) — inserting the same id twice is a no-op, which is what makes
 * re-delivery of the same event over server, mesh and SMS harmless.
 *
 * The local store is a replica of this table, not a cache: rows are never updated or
 * deleted except TTL expiry (CLAUDE.md architecture summary).
 *
 * `@Serializable` because this row *is* the mesh wire format (mesh/MeshProtocol.kt).
 * A separate transport DTO would be the usual decoupling, but here it would be a
 * field-for-field copy of a replicated table: what one device stores is exactly what
 * the next device must store, and a mapping layer between them is somewhere for the
 * two to silently drift apart.
 */
@Serializable
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
    /**
     * Structured, type-specific detail as JSON, for events whose content does not fit
     * the columns above — today only the `sos*` family (see `sos/SosEvents.kt`), whose
     * payload is a people count, medical needs and a water trend rather than a severity.
     *
     * It is a JSON string rather than more columns because the alternative is a table
     * where most rows have most fields null, and because it travels over the mesh as
     * part of the event with no extra handling. Nothing queries inside it: the reducer
     * decodes it in memory, so there is no index to miss.
     */
    val payload: String? = null,
)
