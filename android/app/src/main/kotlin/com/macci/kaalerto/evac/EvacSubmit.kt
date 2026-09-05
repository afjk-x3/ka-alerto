package com.macci.kaalerto.evac

import android.content.Context
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.identity.LocalIdentity
import java.util.UUID

/**
 * BUILD_TASKS.md day 10's "tiny event that rides the mesh". It is an ordinary [Event]
 * with `featureRef = null`, so it reaches other devices through the day 6-7 exchange
 * with no new transport and never appears as a flood marker on the map.
 *
 * Only an official may post one. As with `official/OfficialSubmit.kt`, nothing is
 * signed — a receiving device trusts the role in the event.
 */
suspend fun submitEvacStatus(
    context: Context,
    centre: EvacCentre,
    status: EvacStatus,
    occupancy: Int?,
) {
    val identity = LocalIdentity.getOrCreate(context)
    if (identity.authorRole != LocalIdentity.ROLE_OFFICIAL) return

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
        expiresAt = now + 24 * 60 * 60_000L,
        origin = "local",
        hopCount = 0,
        note = null,
        payload = EvacPayload(centreId = centre.id, status = status.key, occupancy = occupancy).encode(),
    )
    EventRepository(KaAlertoDatabase.getInstance(context).eventDao()).insert(event)
}
