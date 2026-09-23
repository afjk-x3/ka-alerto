package com.macci.kaalerto.family

import android.content.Context
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.identity.LocalIdentity
import java.util.UUID

/** Posts this device's "Ligtas ako". [lat]/[lon] null means the resident declined to
 * share position — see `newCheckInEvent`'s null-island handling. */
suspend fun submitCheckIn(context: Context, lat: Double?, lon: Double?) {
    val identity = LocalIdentity.getOrCreate(context)
    val event = newCheckInEvent(identity, lat, lon, System.currentTimeMillis())
    EventRepository(KaAlertoDatabase.getInstance(context).eventDao()).insert(event)
}

/** Creates a new circle and writes its one circle_create event. Returns the new
 * circleId so the caller can immediately offer it for sharing. */
suspend fun submitCreateCircle(context: Context, name: String): String {
    val identity = LocalIdentity.getOrCreate(context)
    val circleId = "circle-${UUID.randomUUID()}"
    val event = newCircleCreateEvent(identity, circleId, name, System.currentTimeMillis())
    EventRepository(KaAlertoDatabase.getInstance(context).eventDao()).insert(event)
    return circleId
}

/** Joins an existing circle by its id, however the id was obtained (pasted text or a
 * scanned QR — see `family/QrScannerScreen.kt` and `family/JoinCircleScreen.kt`). No
 * existence check against [circleId] before writing: same as every other event this
 * app writes optimistically — see `family/CircleStore.kt`'s `resolveCircle`. */
suspend fun submitJoinCircle(context: Context, circleId: String) {
    val identity = LocalIdentity.getOrCreate(context)
    val event = newCircleJoinEvent(identity, circleId, System.currentTimeMillis())
    EventRepository(KaAlertoDatabase.getInstance(context).eventDao()).insert(event)
}
