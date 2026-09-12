package com.macci.kaalerto.family

import android.content.Context
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.identity.LocalIdentity

/** Posts this device's "Ligtas ako". [lat]/[lon] null means the resident declined to
 * share position — see `newCheckInEvent`'s null-island handling. */
suspend fun submitCheckIn(context: Context, lat: Double?, lon: Double?) {
    val identity = LocalIdentity.getOrCreate(context)
    val event = newCheckInEvent(identity, lat, lon, System.currentTimeMillis())
    EventRepository(KaAlertoDatabase.getInstance(context).eventDao()).insert(event)
}

/** Posts the mutual-pairing invite after this device scans [targetAuthorId]'s QR.
 * [targetAuthorName] is the scanned card's own name — the only place it's guaranteed to
 * be captured for a member reachable only transitively later. See
 * `CircleInvitePayload`'s doc comment. */
suspend fun submitCircleInvite(context: Context, targetAuthorId: String, targetAuthorName: String) {
    val identity = LocalIdentity.getOrCreate(context)
    val event = newCircleInviteEvent(identity, targetAuthorId, targetAuthorName, System.currentTimeMillis())
    EventRepository(KaAlertoDatabase.getInstance(context).eventDao()).insert(event)
}
