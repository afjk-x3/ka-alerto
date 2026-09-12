package com.macci.kaalerto.sos

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/** The rescue card's "how many of you" answer. [ascii] is what goes into the QR. */
enum class PeopleCount(val label: String, val ascii: String) {
    ONE("Ako lang", "1"),
    FEW("2–4 tao", "2-4"),
    MANY("5 pataas", "5+"),
}

/** Everything the rescue card shows, and so everything its QR carries. */
data class RescueCardInfo(
    /** The display form ("Juan D."), or null before registration. */
    val name: String?,
    val homeBarangay: String?,
    val lat: Double?,
    val lon: Double?,
    val accuracyMeters: Float?,
    val people: PeopleCount?,
    val createdAtMs: Long,
)

/**
 * What the QR on this build's rescue card encodes: **plain text any phone camera can
 * read**, not the `KAALERTO/SOS/1:` JSON the feat branch uses. That format exists to be
 * scanned by another copy of the app and injected into the mesh; this build has no mesh
 * and no scanner, so a payload only our own app could parse would be useless to the
 * rescuer standing in front of the phone. Text reads out in any camera app, and the
 * trailing `geo:` line opens straight into a map app on most phones.
 *
 * ASCII apart from the name and barangay the resident typed ("+/-" not "±", "2-4" not
 * "2–4"), because QR readers disagree about character sets and a mangled accuracy figure
 * is worse than a plain one.
 */
fun rescueCardPayload(info: RescueCardInfo, zone: ZoneId = ZoneId.systemDefault()): String {
    val lines = mutableListOf("KAILANGAN NG SAGIP / RESCUE NEEDED")
    val who = listOfNotNull(
        info.name?.trim()?.takeIf { it.isNotEmpty() },
        info.homeBarangay?.trim()?.takeIf { it.isNotEmpty() }?.let { "taga-$it" },
    ).joinToString(", ")
    if (who.isNotEmpty()) lines += who

    val hasFix = info.lat != null && info.lon != null
    if (hasFix) {
        val accuracy = info.accuracyMeters?.let { " (+/-${it.roundToInt()} m)" }.orEmpty()
        lines += "${coord(info.lat!!)}, ${coord(info.lon!!)}$accuracy"
    } else {
        lines += "Lokasyon: hindi makuha"
    }
    info.people?.let { lines += "Ilan: ${it.ascii}" }

    val time = Instant.ofEpochMilli(info.createdAtMs).atZone(zone)
    lines += "Oras: %04d-%02d-%02d %02d:%02d".format(Locale.ROOT, time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute)
    if (hasFix) lines += "geo:${coord(info.lat!!)},${coord(info.lon!!)}"
    return lines.joinToString("\n")
}

/** Five decimals is ~1 m, well inside any GPS fix; Locale.ROOT so it is never "18,17090". */
fun coord(value: Double): String = "%.5f".format(Locale.ROOT, value)

/** A square grid of modules. True is a dark module. */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]
}

/**
 * Encodes to the raw module grid rather than to a bitmap, so the card can draw every
 * module on a whole-pixel boundary at any size — a QR scaled by a fractional factor gets
 * soft edges, which is what makes a code that decodes in a unit test fail against a real
 * camera. Error correction M (~15%): the code may be read off a wet or cracked screen,
 * and H would push a full payload into a denser, harder-to-read version. Ported from
 * feat/event-sourced-roles.
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
