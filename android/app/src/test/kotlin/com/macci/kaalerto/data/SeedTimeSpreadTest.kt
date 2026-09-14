package com.macci.kaalerto.data

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seed fixture's ages have two jobs that pull against each other: spread wide enough
 * that every recency chip (1h / 3h / 24h / Kailanman) changes what the map shows, while
 * the SX pair stays young enough to still render as SX.
 */
class SeedTimeSpreadTest {

    private val reports = Json { ignoreUnknownKeys = true }
        .decodeFromString<SeedFile>(File("src/main/assets/seed_data.json").readText())
        .reports

    private fun countAged(range: LongRange) = reports.count { it.timestampMinutesAgo in range }

    @Test
    fun `every recency chip adds at least one report the narrower one hides`() {
        assertTrue("1h", countAged(0L..60L) >= 1)
        assertTrue("3h", countAged(61L..180L) >= 1)
        assertTrue("24h", countAged(181L..1440L) >= 1)
        assertTrue("Kailanman", countAged(1441L..Long.MAX_VALUE) >= 1)
    }

    @Test
    fun `the live demo is still mostly the last hour`() {
        assertTrue(countAged(0L..60L) >= reports.size / 2)
    }

    /** S0 decays with a 60-min tau; the cleared half drops under the 0.5 SX threshold ~42 min after its timestamp. */
    @Test
    fun `the SX pair is young enough to render as SX`() {
        val pair = reports.filter { it.featureRef == "sotto-street-9" }
        assertTrue(pair.size == 2)
        assertTrue(pair.all { it.timestampMinutesAgo <= 15 })
        val summary = Reducer.summarize("sotto-street-9", pair.map { it.asEvent(now = 1_000_000_000L) }, now = 1_000_000_000L)
        assertTrue(summary!!.isConflicted)
    }

    private fun SeedReport.asEvent(now: Long): Event {
        val timestampMs = now - timestampMinutesAgo * 60_000L
        return Event(
            id = id, type = type, lat = lat, lon = lon, featureRef = featureRef, severity = severity,
            waterLevel = waterLevel, authorId = authorId, authorName = authorName, authorRole = authorRole,
            timestampMs = timestampMs, expiresAt = timestampMs + ttlMinutes * 60_000L, origin = origin,
            hopCount = hopCount, note = note,
        )
    }
}
