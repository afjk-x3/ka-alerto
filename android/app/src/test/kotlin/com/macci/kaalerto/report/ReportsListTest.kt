package com.macci.kaalerto.report

import com.macci.kaalerto.data.FeatureSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportsListTest {

    private fun summary(ref: String, lat: Double = 18.17, lon: Double = 120.60, lastMs: Long = 1_000, stale: Boolean = false) = FeatureSummary(
        featureRef = ref, lat = lat, lon = lon, severity = "S2", confidence = 0.5, bucket = "likely", isConflicted = false,
        lastEventMs = lastMs, isStale = stale, confirmCount = 0, disputeCount = 0, events = emptyList(),
    )

    private fun rows(vararg s: FeatureSummary, lat: Double? = 18.17, lon: Double? = 120.60) = reportRows(s.toList(), lat, lon)
    private fun refs(list: List<ReportRow>) = list.map { it.summary.featureRef }

    // ---- distances ----

    @Test
    fun `distance is measured from this phone, and absent when it has no position`() {
        val near = rows(summary("a", lat = 18.171), lat = 18.17, lon = 120.60).single()
        assertEquals(111.0, near.distanceMeters!!, 5.0)
        assertNull(rows(summary("a"), lat = null, lon = null).single().distanceMeters)
        assertNull(rows(summary("a"), lat = 18.17, lon = null).single().distanceMeters)
    }

    // ---- sorting ----

    @Test
    fun `nearest first puts the closest spot on top`() {
        val list = rows(summary("far", lat = 18.20), summary("near", lat = 18.171), summary("mid", lat = 18.18))
        assertEquals(listOf("near", "mid", "far"), refs(sortReportRows(list, ReportSort.NEAREST)))
    }

    @Test
    fun `newest first puts the most recently updated spot on top`() {
        val list = rows(summary("old", lastMs = 1_000), summary("new", lastMs = 9_000), summary("mid", lastMs = 5_000))
        assertEquals(listOf("new", "mid", "old"), refs(sortReportRows(list, ReportSort.NEWEST)))
    }

    @Test
    fun `expired spots always come last, in either order`() {
        // The expired one is the nearest and the newest, and still goes to the bottom.
        val list = rows(summary("stale", lat = 18.1701, lastMs = 9_999, stale = true), summary("live-far", lat = 18.25, lastMs = 1_000))
        assertEquals(listOf("live-far", "stale"), refs(sortReportRows(list, ReportSort.NEAREST)))
        assertEquals(listOf("live-far", "stale"), refs(sortReportRows(list, ReportSort.NEWEST)))
    }

    @Test
    fun `spots with no known distance follow those that have one, then fall back to newest`() {
        val known = reportRows(listOf(summary("a", lat = 18.17)), 18.17, 120.60)
        val unknown = reportRows(listOf(summary("b", lastMs = 5_000), summary("c", lastMs = 9_000)), null, null)
        assertEquals(listOf("a", "c", "b"), refs(sortReportRows(known + unknown, ReportSort.NEAREST)))
    }

    @Test
    fun `the order does not depend on the order the spots arrive in`() {
        val a = summary("a", lat = 18.17, lastMs = 5_000)
        val b = summary("b", lat = 18.17, lastMs = 5_000)
        val c = summary("c", lat = 18.17, lastMs = 5_000)
        assertEquals(refs(sortReportRows(rows(a, b, c), ReportSort.NEAREST)), refs(sortReportRows(rows(c, a, b), ReportSort.NEAREST)))
    }

    @Test
    fun `an empty list sorts to an empty list`() {
        assertEquals(emptyList<ReportRow>(), sortReportRows(emptyList(), ReportSort.NEAREST))
    }

    // ---- distance text ----

    @Test
    fun `distance text rounds to ten metres, then switches to kilometres`() {
        assertEquals("0 m", shortDistance(3.0))
        assertEquals("120 m", shortDistance(118.0))
        assertEquals("990 m", shortDistance(994.0))
        assertEquals("1.2 km", shortDistance(1_240.0))
        assertEquals("240.5 km", shortDistance(240_500.0))
    }
}
