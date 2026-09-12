package com.macci.kaalerto.detail

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Plain-language labels for the detail sheet, kept out of the composables so they can be
 * unit-tested. Written for a resident reading quickly under stress: whole Filipino words
 * instead of jargon ("Seed data", "hops", "Direkta"), and a calendar day next to every
 * clock time — "10:36 AM" on its own cannot tell today's report from last week's.
 */

/** Where a report came from: a short answer, plus an optional helper line beneath it. */
data class OriginText(val main: String, val helper: String?)

fun originText(origin: String, hopCount: Int): OriginText = when (origin) {
    "local" -> OriginText("Mula sa phone mo", "Ikaw ang nag-ulat nito")
    // Seed fixtures never travelled between phones, so they are labelled as the demo
    // samples they are — never dressed up as a neighbour's report.
    "seed" -> OriginText("Halimbawang ulat", "Kasama sa app, pang-demo")
    "mesh" -> OriginText("Ipinasa ng kalapit na phone", hopCount.takeIf { it > 0 }?.let { "Dumaan sa $it phone" })
    "sms" -> OriginText("Galing sa text", "Ipinadala sa SMS")
    "server" -> OriginText("Galing sa internet", "Kinuha mula sa server")
    else -> OriginText("Hindi alam kung saan galing", null)
}

private val MONTHS = listOf(
    "Enero", "Pebrero", "Marso", "Abril", "Mayo", "Hunyo",
    "Hulyo", "Agosto", "Setyembre", "Oktubre", "Nobyembre", "Disyembre",
)

/** "Ngayong araw", "Kahapon", "Setyembre 10", or "Disyembre 30, 2025" — by calendar day, not elapsed hours. */
fun reportedDayLabel(
    timestampMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val day = localDate(timestampMs, zone)
    val today = localDate(nowMs, zone)
    val monthDay = "${MONTHS[day.monthValue - 1]} ${day.dayOfMonth}"
    return when {
        day == today -> "Ngayong araw"
        day == today.minusDays(1) -> "Kahapon"
        day.year == today.year -> monthDay
        else -> "$monthDay, ${day.year}"
    }
}

/**
 * "10:36 AM". Built by hand rather than with a `h:mm a` formatter: newer locale data puts
 * a narrow no-break space before AM/PM on some devices and not others, and some locales
 * swap in non-Latin digits.
 */
fun reportedTimeLabel(timestampMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalTime()
    val hour12 = (time.hour % 12).let { if (it == 0) 12 else it }
    val suffix = if (time.hour < 12) "AM" else "PM"
    return "$hour12:${time.minute.toString().padStart(2, '0')} $suffix"
}

/** "Ngayong araw, 10:36 AM" — the day and time together, for anywhere with room for both. */
fun reportedAtLabel(
    timestampMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String = "${reportedDayLabel(timestampMs, nowMs, zone)}, ${reportedTimeLabel(timestampMs, zone)}"

private fun localDate(epochMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

/** "7 minuto na ang nakalipas" — whole words rather than "7 min ago". */
fun ageLabel(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    return when {
        minutes < 1 -> "Ngayon lang"
        minutes < 60 -> "$minutes minuto na ang nakalipas"
        hours < 24 && minutes % 60 == 0L -> "$hours oras na ang nakalipas"
        hours < 24 -> "$hours oras at ${minutes % 60} minuto na ang nakalipas"
        else -> "${hours / 24} araw na ang nakalipas"
    }
}

fun bucketLabel(bucket: String): String = when (bucket) {
    "official" -> "Opisyal na ulat"
    "confirmed" -> "Kumpirmado"
    "likely" -> "Malamang totoo"
    else -> "Hindi pa kumpirmado"
}

fun roleLabel(role: String): String = when (role) {
    "official" -> "Opisyal ng barangay"
    "responder" -> "Tagasagip"
    else -> "Residente"
}

/** Uses the words on the two buttons ("Tama" / "Iba na") so the count explains itself. */
fun agreementLabel(confirms: Int, disputes: Int): String =
    "$confirms nagsabing tama · $disputes nagsabing iba na"

/** A stored dispute reason ("cleared_now") as the option the resident actually tapped. */
fun disputeReasonLabel(reason: String?): String = when (reason) {
    "cleared_now" -> "Humupa na"
    "worse" -> "Lumala"
    "shallower" -> "Bumaba"
    "wrong_location" -> "Maling lokasyon"
    else -> "Iba na"
}
