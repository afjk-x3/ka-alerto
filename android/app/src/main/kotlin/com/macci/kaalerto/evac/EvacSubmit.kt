package com.macci.kaalerto.evac

import android.content.Context
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.identity.LocalIdentity
import java.util.UUID

private const val DAY_MS = 24 * 60 * 60_000L

/** A shelter definition outlives a flood: a year, like role events. */
private const val CENTRE_TTL_MS = 365 * DAY_MS

/**
 * BUILD_TASKS.md day 10's "tiny event that rides the mesh". It is an ordinary [Event]
 * with `featureRef = null`, so it reaches other devices through the day 6-7 exchange
 * with no new transport and never appears as a flood marker on the map.
 *
 * Only an official may post one, and only for a shelter in their own municipality: the phone
 * refuses otherwise, stamps its municipality on the update, and [evacStates] ignores an update
 * from another municipality. As with `official/OfficialSubmit.kt`, nothing is signed — a
 * receiving device trusts the role in the event.
 */
suspend fun submitEvacStatus(
    context: Context,
    centre: EvacCentre,
    status: EvacStatus,
    occupancy: Int?,
): Boolean {
    val identity = LocalIdentity.getOrCreate(context)
    if (identity.authorRole != LocalIdentity.ROLE_OFFICIAL) return false
    val municipality = LocalIdentity.homeMunicipality(context)
    if (!canManage(municipality, centre)) return false

    val now = System.currentTimeMillis()
    val event = Event(
        id = "evac-${UUID.randomUUID()}",
        type = TYPE_EVAC_STATUS,
        lat = centre.lat,
        lon = centre.lon,
        // Null: this is a fact about a building, not an observation of a flooded
        // segment. A featureRef here would put a severity marker on the school.
        featureRef = null,
        severity = null,
        waterLevel = null,
        authorId = identity.authorId,
        authorName = identity.authorName,
        authorRole = identity.authorRole,
        timestampMs = now,
        // A centre's status is good for a day. The artboard's own footer worries about
        // exactly this ("Kapasidad: 12 min ang tanda"), so the screen shows the time it
        // was set and lets the reader judge.
        expiresAt = now + DAY_MS,
        origin = "local",
        hopCount = 0,
        note = null,
        payload = EvacPayload(centreId = centre.id, status = status.key, occupancy = occupancy, municipality = municipality.trim()).encode(),
    )
    EventRepository(KaAlertoDatabase.getInstance(context).eventDao()).insert(event)
    return true
}

/** An official may add or change only shelters in their own municipality; with none set they may manage none. */
fun canManage(officialMunicipality: String?, centre: EvacCentre): Boolean =
    sameMunicipality(officialMunicipality, centre.municipality)

/** The `evac_centre` event for adding a shelter, or, with [removed], hiding one the same municipality added. */
fun newEvacCentreEvent(
    author: LocalIdentity.Identity,
    payload: EvacCentrePayload,
    nowMs: Long,
): Event = Event(
    id = "centre-${UUID.randomUUID()}",
    type = TYPE_EVAC_CENTRE,
    lat = payload.lat,
    lon = payload.lon,
    featureRef = null,
    severity = null,
    waterLevel = null,
    authorId = author.authorId,
    authorName = author.authorName,
    authorRole = author.authorRole,
    timestampMs = nowMs,
    expiresAt = nowMs + CENTRE_TTL_MS,
    origin = "local",
    hopCount = 0,
    note = null,
    payload = payload.encode(),
)

/** Adds a shelter in the official's own municipality. Returns its id, or null when they may not (not an official, or no municipality set). */
suspend fun submitEvacCentre(
    context: Context,
    name: String,
    kind: String,
    lat: Double,
    lon: Double,
    barangay: String?,
    capacityEstimate: Int?,
): String? {
    val identity = LocalIdentity.getOrCreate(context)
    val municipality = LocalIdentity.homeMunicipality(context).trim()
    if (identity.authorRole != LocalIdentity.ROLE_OFFICIAL || municipality.isEmpty() || name.isBlank()) return null

    val id = "evac-${UUID.randomUUID()}"
    val payload = EvacCentrePayload(
        centreId = id,
        name = name.trim(),
        kind = if (kind in EVAC_KINDS) kind else "other",
        lat = lat,
        lon = lon,
        municipality = municipality,
        barangay = barangay?.trim()?.ifBlank { null },
        capacityEstimate = capacityEstimate?.takeIf { it > 0 },
    )
    EventRepository(KaAlertoDatabase.getInstance(context).eventDao()).insert(newEvacCentreEvent(identity, payload, System.currentTimeMillis()))
    return id
}

/** Hides a shelter an official added (never a bundled one), in their own municipality. */
suspend fun removeEvacCentre(context: Context, centre: EvacCentre): Boolean {
    val identity = LocalIdentity.getOrCreate(context)
    val municipality = LocalIdentity.homeMunicipality(context)
    if (identity.authorRole != LocalIdentity.ROLE_OFFICIAL || !centre.custom || !canManage(municipality, centre)) return false

    val payload = EvacCentrePayload(
        centreId = centre.id,
        name = centre.name,
        kind = centre.kind,
        lat = centre.lat,
        lon = centre.lon,
        municipality = municipality.trim(),
        barangay = centre.barangay,
        capacityEstimate = centre.capacityEstimate,
        removed = true,
    )
    EventRepository(KaAlertoDatabase.getInstance(context).eventDao()).insert(newEvacCentreEvent(identity, payload, System.currentTimeMillis()))
    return true
}
