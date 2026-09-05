package com.macci.kaalerto.official

import android.content.Context
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.data.ttlMinutesFor
import com.macci.kaalerto.identity.LocalIdentity
import java.util.UUID

/** An official's ruling on one feature. Rule D in `data/Reducer.kt` reads this. */
const val TYPE_OFFICIAL_STATUS = "official_status"

/**
 * Posts an official status for a feature.
 *
 * It is an ordinary [Event] like every other, which is the point: it rides the day 6-7
 * mesh with no new transport, it dedupes on re-delivery, and it never deletes the
 * resident reports it overrides — the reducer folds it alongside them
 * (OfficialVerify.dc.html: "Hindi binubura ng opisyal na status ang mga ito").
 *
 * Nothing here is signed. `docs/03-architecture.md` §2.5 wants official events carrying
 * a signature that a receiving device verifies; ground rule 4 puts crypto out of scope,
 * so what actually travels is the author's name and role, and a receiving device trusts
 * it. That is a real gap, not an oversight — a device on this mesh cannot tell a genuine
 * kagawad from anyone who flipped the role switch.
 */
suspend fun submitOfficialStatus(
    context: Context,
    featureRef: String,
    lat: Double,
    lon: Double,
    severity: String,
) {
    val identity = LocalIdentity.getOrCreate(context)
    if (identity.authorRole != LocalIdentity.ROLE_OFFICIAL) return

    val now = System.currentTimeMillis()
    val event = Event(
        id = "official-${UUID.randomUUID()}",
        type = TYPE_OFFICIAL_STATUS,
        lat = lat,
        lon = lon,
        featureRef = featureRef,
        severity = severity,
        waterLevel = null,
        authorId = identity.authorId,
        authorName = identity.authorName,
        authorRole = identity.authorRole,
        timestampMs = now,
        // An official ruling outlives a resident report of the same severity: it is the
        // barangay's position on the road, not one person's glance at it.
        expiresAt = now + ttlMinutesFor(severity) * 2 * 60_000L,
        origin = "local",
        hopCount = 0,
        note = null,
    )
    EventRepository(KaAlertoDatabase.getInstance(context).eventDao()).insert(event)
}
