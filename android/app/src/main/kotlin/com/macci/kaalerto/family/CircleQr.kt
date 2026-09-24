package com.macci.kaalerto.family

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Marks the payload as a circle-join card, distinct from `sos/SosQr.kt`'s
 * `KAALERTO/SOS/1:` prefix — a scanner (or a person eyeballing a raw scan result) can
 * tell the two apart, and `decodeCircleJoinCard` rejects anything that isn't this.
 * Version bumped from `/1:` (the old pairwise-pairing card) to `/2:` (this circle-id
 * card) so a stray old-format QR is rejected cleanly rather than misparsed —
 * see `notes/specs/2026-09-23-circle-create-join-redesign.md`. */
const val CIRCLE_QR_PREFIX = "KAALERTO/CIRCLE/2:"

/** What a circle-join QR encodes — enough to write one [TYPE_CIRCLE_JOIN] event for
 * the scanned circle, and to show the circle's name before scanning it (see
 * `family/MyCircleQrScreen.kt`). Short keys for the same reason `sos/SosQr.kt`'s
 * `SosCard` uses them: QR capacity is the binding constraint. */
@Serializable
data class CircleJoinCard(
    @SerialName("id") val circleId: String,
    @SerialName("n") val name: String,
)

private val cardJson = Json { ignoreUnknownKeys = true }

fun CircleJoinCard.encode(): String = CIRCLE_QR_PREFIX + cardJson.encodeToString(CircleJoinCard.serializer(), this)

/** Returns null for anything that is not one of our circle-join codes, or that will not parse. */
fun decodeCircleJoinCard(scanned: String): CircleJoinCard? {
    if (!scanned.startsWith(CIRCLE_QR_PREFIX)) return null
    return runCatching {
        cardJson.decodeFromString(CircleJoinCard.serializer(), scanned.removePrefix(CIRCLE_QR_PREFIX))
    }.getOrNull()
}
