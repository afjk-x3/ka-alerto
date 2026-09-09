package com.macci.kaalerto.family

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Marks the payload as a circle-pairing card, distinct from `sos/SosQr.kt`'s
 * `KAALERTO/SOS/1:` prefix — a scanner (or a person eyeballing a raw scan result) can
 * tell the two apart, and `decodeCircleCard` rejects anything that isn't this. */
const val CIRCLE_QR_PREFIX = "KAALERTO/CIRCLE/1:"

/** What a circle-pairing QR encodes — just enough to add the other device to a circle
 * and post the mutual [TYPE_CIRCLE_INVITE] back. Short keys for the same reason
 * `sos/SosQr.kt`'s `SosCard` uses them: QR capacity is the binding constraint. */
@Serializable
data class CircleCard(
    @SerialName("id") val authorId: String,
    @SerialName("n") val authorName: String,
)

private val cardJson = Json { ignoreUnknownKeys = true }

fun CircleCard.encode(): String = CIRCLE_QR_PREFIX + cardJson.encodeToString(CircleCard.serializer(), this)

/** Returns null for anything that is not one of our circle-pairing codes, or that will not parse. */
fun decodeCircleCard(scanned: String): CircleCard? {
    if (!scanned.startsWith(CIRCLE_QR_PREFIX)) return null
    return runCatching {
        cardJson.decodeFromString(CircleCard.serializer(), scanned.removePrefix(CIRCLE_QR_PREFIX))
    }.getOrNull()
}
