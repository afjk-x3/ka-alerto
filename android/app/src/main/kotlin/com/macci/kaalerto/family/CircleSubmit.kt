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

/** Posts the mutual-pairing invite after this device scans [targetAuthorId]'s QR. */
suspend fun submitCircleInvite(context: Context, targetAuthorId: String) {
    val identity = LocalIdentity.getOrCreate(context)
    val event = newCircleInviteEvent(identity, targetAuthorId, System.currentTimeMillis())
    EventRepository(KaAlertoDatabase.getInstance(context).eventDao()).insert(event)
}
