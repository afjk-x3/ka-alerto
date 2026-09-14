package com.macci.kaalerto.map

import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.FeatureSummary
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportsTodayTest {

    private val manila = ZoneId.of("Asia/Manila")

    private fun at(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 9, day, hour, minute).atZone(manila).toInstant().toEpochMilli()

    private fun report(id: String, timestampMs: Long, type: String = "flood_report") = Event(
        id = id, type = type, lat = 18.17, lon = 120.60, featureRef = "f-$id", severity = "S1",
        waterLevel = null, authorId = "a-$id", authorName = "Juan D.", authorRole = "resident",
        timestampMs = timestampMs, expiresAt = timestampMs + 3_600_000L, origin = "seed", hopCount = 0, note = null,
    )

    private fun summaryOf(vararg events: Event) = FeatureSummary(
        featureRef = "f", lat = 18.17, lon = 120.60, severity = "S1", confidence = 0.0, bucket = "unverified",
        isConflicted = false, lastEventMs = events.maxOf { it.timestampMs }, isStale = false,
        confirmCount = 0, disputeCount = 0, events = events.toList(),
    )

    private fun <T> withPhoneZone(id: String, block: () -> T): T {
        val saved = TimeZone.getDefault()
        return try {
            TimeZone.setDefault(TimeZone.getTimeZone(id))
            block()
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    /**
     * 7:49 AM Philippine time is 11:49 PM the previous day in UTC. On a phone left on UTC
     * the header used to drop it from "ulat ngayong araw" — while the detail sheet, which
     * is pinned to Philippine time, called the same report "Ngayong araw".
     */
    @Test
    fun `today is the Philippine calendar day whatever the phone's zone`() {
        val now = at(14, 15, 49)
        val summaries = listOf(summaryOf(report("morning", at(14, 7, 49)), report("yesterday", at(13, 14, 0))))
        assertEquals(1, withPhoneZone("UTC") { reportsToday(summaries, now) })
        assertEquals(1, withPhoneZone("America/Los_Angeles") { reportsToday(summaries, now) })
    }

    @Test
    fun `confirms and disputes are not reports, and a report is counted once`() {
        val now = at(14, 15, 0)
        val first = report("r1", at(14, 9, 0))
        val summaries = listOf(summaryOf(first, report("c1", at(14, 10, 0), type = "confirm")), summaryOf(first))
        assertEquals(1, reportsToday(summaries, now))
    }
}
