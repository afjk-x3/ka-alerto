package com.macci.kaalerto.sos

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the rescue card's QR actually encodes.
 *
 * `docs/03-architecture.md` §6.4.3: the code carries the full SOS payload "so any
 * passing app user can scan it and inject the request into the mesh **without needing
 * any radio link to the originating device at all**". So this is the request, not a
 * link to it — a URL would be useless to the person it is designed for, who by
 * definition has no network.
 *
 * Keys are one and two characters because QR capacity is the binding constraint and
 * every byte spent on `"accuracyMeters"` is a byte of error correction given up on a
 * code someone will photograph through a window, at night, from a boat.
 */
@Serializable
data class SosCard(
    @SerialName("v") val version: Int = 1,
    @SerialName("id") val sosId: String,
    @SerialName("t") val timestampMs: Long,
    @SerialName("la") val lat: Double,
    @SerialName("lo") val lon: Double,
    @SerialName("ac") val accuracyMeters: Float? = null,
    @SerialName("n") val authorName: String,
    @SerialName("p") val people: String? = null,
    @SerialName("c") val companions: List<String> = emptyList(),
    @SerialName("m") val medical: List<String> = emptyList(),
    @SerialName("w") val water: String? = null,
    @SerialName("tr") val trend: String? = null,
)

/**
 * Marks the payload as ours so a general-purpose scanner shows something recognisable
 * and our own scanner (day 9's inbound half) can reject a QR that merely happens to
 * contain JSON.
 */
const val SOS_QR_PREFIX = "KAALERTO/SOS/1:"

private val cardJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

fun SosSnapshot.toCard(): SosCard = SosCard(
    sosId = sosId,
    timestampMs = startedAtMs,
    lat = lat,
    lon = lon,
    accuracyMeters = accuracyMeters,
    authorName = authorName,
    people = context.people,
    companions = context.companions,
    medical = context.medical,
    water = context.water,
    trend = context.trend,
)

fun SosCard.encode(): String = SOS_QR_PREFIX + cardJson.encodeToString(SosCard.serializer(), this)

/** Returns null for anything that is not one of our codes, or that will not parse. */
fun decodeSosCard(scanned: String): SosCard? {
    if (!scanned.startsWith(SOS_QR_PREFIX)) return null
    return runCatching {
        cardJson.decodeFromString(SosCard.serializer(), scanned.removePrefix(SOS_QR_PREFIX))
    }.getOrNull()
}

/** A square grid of modules. True is a dark module. */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]
}

/**
 * Encodes to the raw module grid rather than to a bitmap.
 *
 * `QRCodeWriter` would rasterise to a fixed pixel size, which then has to be scaled to
 * whatever the screen gives us — and a QR scaled by a non-integer factor develops
 * blurred module edges, which is exactly what makes a code fail to scan. Drawing the
 * grid ourselves on a Compose Canvas means every module lands on the same rounded
 * boundary at any card size.
 *
 * Error correction M (~15%) rather than L: this code gets read off a screen that may be
 * wet, cracked, or photographed at an angle. H would be more robust still, but costs
 * enough capacity to push a full payload into a denser version, and denser modules on a
 * small phone screen lose more than the extra correction wins.
 */
fun encodeQr(content: String): QrMatrix {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    val matrix = Encoder.encode(content, ErrorCorrectionLevel.M, hints).matrix
        ?: error("ZXing produced no matrix for ${content.length} chars")

    val size = matrix.width
    val dark = BooleanArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            dark[y * size + x] = matrix.get(x, y).toInt() == 1
        }
    }
    return QrMatrix(size, dark)
}
