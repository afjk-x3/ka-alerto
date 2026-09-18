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
     * the columns above. Each family that uses it owns its shape and a `decode…Payload`
     * beside its event factory:
     *
     * - `sos`, `sos_amend`, `sos_state` — `SosPayload` (`sos/SosEvents.kt`): the request
     *   id plus GPS accuracy, the context answers, or the new state.
     * - `sos_false_alarm`, `sos_false_alarm_undo` — `FalseAlarmPayload` (`sos/SosTriage.kt`).
     * - `role_claim`, `role_request`, `role_grant`, `role_revoke` — `RolePayload`
     *   (`identity/RoleEvents.kt`).
     * - `circle_invite` — `CircleInvitePayload` (`family/CircleEvents.kt`).
     * - `evac_status` — `EvacPayload` (`evac/EvacCentre.kt`): centre, status, occupancy.
     * - `flood_report` — `ReportPhotoPayload` (`report/ReportPhoto.kt`), only when a photo
     *   is attached, and only its hash: the image itself never travels.
     *
     * `confirm`, `dispute`, `official_status` and `family_checkin` carry none. This list
     * is easy to outgrow — `fun decode\w*Payload` in `main/` is the authoritative one.
     *
     * It is a JSON string rather than more columns because the alternative is a table
     * where most rows have most fields null. Nothing queries inside it: each fold decodes
     * it in memory, so there is no index to miss. It rides mesh and server sync as part
     * of the event, unchanged with one exception — `sos/SosMeshPolicy.kt`'s
     * `redactSosOnEgress` strips medical detail from an SOS payload before it leaves the
     * device (it blanks [authorName] in the same pass).
     */
    val payload: String? = null,
)
