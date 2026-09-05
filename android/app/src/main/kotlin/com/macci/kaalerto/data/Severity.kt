package com.macci.kaalerto.data

/** S0..S3 ordered low to high. "SX" (conflicting) is deliberately not part of this ladder — see Reducer.kt. */
private val ORDER = listOf("S0", "S1", "S2", "S3")

fun severityOrdinal(severity: String): Int = ORDER.indexOf(severity).coerceAtLeast(0)

fun severityUp(severity: String): String = ORDER.getOrElse(severityOrdinal(severity) + 1) { ORDER.last() }

fun severityDown(severity: String): String = ORDER.getOrElse(severityOrdinal(severity) - 1) { ORDER.first() }

/**
 * Severity-dependent TTL in minutes — how long an observation of this severity stays
 * relevant before it's stale. Used both as an event's `expiresAt` offset and, as
 * [decayTauMinutesFor], as the reducer's decay time constant `τ`
 * (docs/03-architecture.md §5.2: "higher severity → longer τ") — a report's TTL and
 * its decay timescale are the same real-world quantity. Matches the seed fixtures'
 * own table (`seed_data.json`) exactly.
 */
fun ttlMinutesFor(severity: String?): Long = when (severity) {
    "S1" -> 120L
    "S2" -> 240L
    "S3" -> 360L
    else -> 60L
}

fun decayTauMinutesFor(severity: String): Double = ttlMinutesFor(severity).toDouble()

/** Severity-tier copy shared by the report picker and the detail sheet — one canonical FIL/EN pair per tier, not per screen. */
private val SEVERITY_TEXT = mapOf(
    "S1" to ("Madaanan, mag-ingat" to "Passable with caution"),
    "S2" to ("Hindi madaanan ng sasakyan" to "Impassable for cars"),
    "S3" to ("Hindi madaanan" to "Impassable for all"),
)

fun severityTextFor(severity: String): Pair<String, String> = SEVERITY_TEXT[severity] ?: ("Hindi tiyak" to "Unknown")
