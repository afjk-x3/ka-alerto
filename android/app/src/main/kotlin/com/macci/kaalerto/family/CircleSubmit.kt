package com.macci.kaalerto.family

import android.content.Context
import com.macci.kaalerto.data.Event
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

private val CIRCLE_ID_PATTERN = Regex("circle-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/** Pulls a circleId out of arbitrary pasted text — the share message wraps it in a
 * sentence, and a long-press Copy in a messaging app copies the whole sentence, not
 * just the code. Returns null when nothing matching is found. */
fun extractCircleId(text: String): String? = CIRCLE_ID_PATTERN.find(text)?.value

/**
 * An 8-digit code that can be read out over a call ("4821-0937"), derived from the circle
 * id so it needs no new event and works for circles made before it existed. Digits only:
 * letters like B, D and E sound alike on a bad line. 10^8 codes is plenty for a barangay.
 */
fun shortCircleCode(circleId: String): String {
    val n = circleId.removePrefix("circle-").replace("-", "").take(12).toLong(16) % 100_000_000
    return "%04d-%04d".format(n / 10_000, n % 10_000)
}

private val SHORT_CODE_PATTERN = Regex("""(?<!\d)\d{4}[- ]?\d{4}(?!\d)""")

/**
 * The circle a typed or pasted code means: a full circle id anywhere in [text] first,
 * otherwise an 8-digit [shortCircleCode] matched against the circles this phone already
 * knows from their `circle_create` events. Null when neither finds one — for a short code
 * that usually means the circle has not synced to this phone yet.
 */
fun resolveJoinCode(text: String, events: List<Event>): String? {
    extractCircleId(text)?.let { return it }
    val digits = SHORT_CODE_PATTERN.find(text)?.value?.filter(Char::isDigit) ?: return null
    return events.asSequence()
        .filter { it.type == TYPE_CIRCLE_CREATE }
        .mapNotNull { decodeCircleCreatePayload(it.payload)?.circleId }
        .firstOrNull { shortCircleCode(it).filter(Char::isDigit) == digits }
}
