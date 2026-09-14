package com.macci.kaalerto.detail

import com.macci.kaalerto.i18n.AppLanguage
import com.macci.kaalerto.i18n.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Plain-language report labels, ported from passable-v0 (c79ea4d, bb05608) and made
 * language-aware for this branch's Filipino/English toggle. Written for someone reading
 * quickly under stress: whole words instead of jargon ("Seed data", "hops", "Direkta"),
 * and a calendar day next to every clock time — "10:36 AM" alone cannot tell today's
 * report from last week's. Times are in the phone's own zone, as everywhere else on this
 * branch (V0 pinned Philippine time; that was deliberately not ported). Plain functions so
 * they can be unit-tested; composables pass `LocalAppLanguage.current`.
 */

/** Where a report came from: a short answer, plus an optional helper line beneath it. */
data class OriginText(val main: String, val helper: String?)

fun originText(origin: String, hopCount: Int, language: AppLanguage): OriginText = when (origin) {
    "local" -> OriginText(tr(language, "Mula sa phone mo", "From your phone"), tr(language, "Ikaw ang nag-ulat nito", "You reported this"))
    // Seed fixtures are labelled as the demo samples they are — never dressed up as a neighbour's report.
    "seed" -> OriginText(tr(language, "Halimbawang ulat", "Sample report"), tr(language, "Kasama sa app, pang-demo", "Comes with the app, for the demo"))
    "mesh" -> OriginText(
        tr(language, "Ipinasa ng kalapit na phone", "Passed on by a nearby phone"),
        hopCount.takeIf { it > 0 }?.let { tr(language, "Dumaan sa $it phone", if (it == 1) "Through 1 phone" else "Through $it phones") },
    )
    "sms" -> OriginText(tr(language, "Galing sa text", "From a text message"), tr(language, "Ipinadala sa SMS", "Sent by SMS"))
    "server" -> OriginText(tr(language, "Galing sa internet", "From the internet"), tr(language, "Kinuha mula sa server", "Fetched from the server"))
    else -> OriginText(tr(language, "Hindi alam kung saan galing", "Unknown source"), null)
}

private val MONTHS_FIL = listOf(
    "Enero", "Pebrero", "Marso", "Abril", "Mayo", "Hunyo",
    "Hulyo", "Agosto", "Setyembre", "Oktubre", "Nobyembre", "Disyembre",
)

private val MONTHS_EN = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** "Ngayong araw", "Kahapon", "Setyembre 10", or "Disyembre 30, 2025" — by calendar day, not elapsed hours. */
fun reportedDayLabel(
    timestampMs: Long,
    language: AppLanguage,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val day = localDate(timestampMs, zone)
    val today = localDate(nowMs, zone)
    val months = if (language == AppLanguage.EN) MONTHS_EN else MONTHS_FIL
    val monthDay = "${months[day.monthValue - 1]} ${day.dayOfMonth}"
    return when {
        day == today -> tr(language, "Ngayong araw", "Today")
        day == today.minusDays(1) -> tr(language, "Kahapon", "Yesterday")
        day.year == today.year -> monthDay
        else -> "$monthDay, ${day.year}"
    }
}

/**
 * "10:36 AM". Built by hand rather than with an `h:mm a` formatter: newer locale data puts
 * a narrow no-break space before AM/PM on some devices and not others, and some locales
 * swap in non-Latin digits.
 */
fun reportedTimeLabel(timestampMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalTime()
    val hour12 = (time.hour % 12).let { if (it == 0) 12 else it }
    val suffix = if (time.hour < 12) "AM" else "PM"
    return "$hour12:${time.minute.toString().padStart(2, '0')} $suffix"
}

/** "Ngayong araw, 10:36 AM" — the day and time together. */
fun reportedAtLabel(
    timestampMs: Long,
    language: AppLanguage,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String = "${reportedDayLabel(timestampMs, language, nowMs, zone)}, ${reportedTimeLabel(timestampMs, zone)}"

private fun localDate(epochMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

/** "7 minuto na ang nakalipas" — whole words rather than "7 min ang nakalipas". */
fun ageLabel(ms: Long, language: AppLanguage): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> tr(language, "Ngayon lang", "Just now")
        minutes < 60 -> tr(language, "$minutes minuto na ang nakalipas", "$minutes min ago")
        hours < 24 && minutes % 60 == 0L -> tr(language, "$hours oras na ang nakalipas", "$hours h ago")
        hours < 24 -> tr(language, "$hours oras at ${minutes % 60} minuto na ang nakalipas", "$hours h ${minutes % 60} min ago")
        else -> tr(language, "$days araw na ang nakalipas", if (days == 1L) "1 day ago" else "$days days ago")
    }
}

fun bucketLabel(bucket: String, language: AppLanguage): String = when (bucket) {
    "official" -> tr(language, "Opisyal na ulat", "Official report")
    "confirmed" -> tr(language, "Kumpirmado", "Confirmed")
    "likely" -> tr(language, "Malamang totoo", "Likely true")
    else -> tr(language, "Hindi pa kumpirmado", "Not yet confirmed")
}
