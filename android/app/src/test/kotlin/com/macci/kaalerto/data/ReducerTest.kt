package com.macci.kaalerto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Day 4's reducer — Rules A-D, the weighting that feeds them, Wilson confidence and the
 * buckets.
 *
 * Until 7 September this had no test file of its own: the whole of it was exercised only
 * incidentally, through day 10's [OfficialOverrideTest], which is coverage of Rule D that
 * happens to run the rest. For the piece the project calls its intellectual core, and the
 * one thing making an offline phone trustworthy rather than merely stale (NFR-4), that
 * was the thinnest coverage in the build.
 *
 * The asymmetry these tests exist to protect: **raising a severity takes one credible
 * voice, lowering one takes corroboration.** Every rule below is a variation on it, and
 * a change that quietly reverses it would make the map dangerous rather than wrong.
 */
class ReducerTest {

    private val now = 1_700_000_000_000L
    private val ref = "wz0j1abc"

    /** The feature's anchor. Everything within ~30 m of here counts as proximate (×1.0). */
    private val anchorLat = 18.1709
    private val anchorLon = 120.6058

    /** ~900 m north — outside the 500 m proximate band, so ×0.4 and not a Rule B observer. */
    private val farLat = 18.1790

    private fun report(
        author: String,
        severity: String,
        role: String = "resident",
        minutesAgo: Long = 5,
        lat: Double = anchorLat,
        lon: Double = anchorLon,
        type: String = "flood_report",
        id: String = "$author-$severity-$minutesAgo-$type",
    ) = Event(
        id = id,
        type = type,
        lat = lat,
        lon = lon,
        featureRef = ref,
        severity = severity,
        waterLevel = null,
        authorId = author,
        authorName = author,
        authorRole = role,
        timestampMs = now - minutesAgo * 60_000,
        expiresAt = now - minutesAgo * 60_000 + ttlMinutesFor(severity) * 60_000,
        origin = "local",
        hopCount = 0,
        note = null,
    )

    private fun summarize(vararg events: Event) = Reducer.summarize(ref, events.toList(), now)!!

    // ---- shape ----

    @Test
    fun `a feature with no events has no summary`() {
        assertNull(Reducer.summarize(ref, emptyList(), now))
        assertNull(Reducer.summarize("somewhere-else", listOf(report("a", "S2")), now))
    }

    @Test
    fun `summarizeAll groups by featureRef and ignores events that have none`() {
        val other = report("b", "S1").copy(id = "other", featureRef = "other-ref")
        val unplaced = report("c", "S3").copy(id = "unplaced", featureRef = null)
        val all = Reducer.summarizeAll(listOf(report("a", "S2"), other, unplaced), now)
        assertEquals(setOf(ref, "other-ref"), all.map { it.featureRef }.toSet())
    }

    // ---- Rule A: the highest claimed tier is the ceiling ----

    @Test
    fun `Rule A's ceiling holds until Rule B's bar is met`() {
        // Both cases use only danger tiers, so Rule C's safe-side weight stays at zero
        // and the two rules can be seen without SX in the way.
        //
        // One lower observer: the ceiling stands. This is Rule A — a single credible
        // higher report is not outvoted by one lower one.
        assertEquals("S3", summarize(report("a", "S3"), report("b", "S2")).severity)

        // Two proximate lower observers: the bar is met and the display comes down.
        // Written out because it is easy to read Rule A as absolute and then "fix" the
        // reducer to make it so, which would mean nothing could ever be marked passable
        // again once one person had called it impassable.
        assertEquals(
            "S2",
            summarize(report("a", "S3"), report("b", "S2"), report("c", "S2")).severity,
        )
    }

    @Test
    fun `a report too decayed to matter cannot set the ceiling`() {
        // S1 decays with tau = 120 min. At 40 hours its weight is far below WEIGHT_FLOOR,
        // so it must not hold the display up at S1 against a live S0.
        val summary = summarize(report("old", "S1", minutesAgo = 40 * 60), report("now", "S0"))
        assertEquals("S0", summary.severity)
    }

    @Test
    fun `with every report decayed away the feature reads S0 and unverified`() {
        val summary = summarize(report("a", "S3", minutesAgo = 100 * 60))
        assertEquals("S0", summary.severity)
        assertEquals("unverified", summary.bucket)
        assertEquals(0.0, summary.confidence, 1e-9)
    }

    // ---- Rule B: lowering needs two proximate observers ----

    @Test
    fun `one nearby resident cannot pull the display down from S3`() {
        val summary = summarize(report("a", "S3"), report("b", "S0"))
        // Not lowered — and with danger and safe weight both present this is the
        // disagreement Rule C exists for.
        assertTrue(summary.isConflicted)
        assertEquals("SX", summary.severity)
    }

    @Test
    fun `two proximate residents do pull it down, and that pre-empts SX`() {
        val summary = summarize(report("a", "S3"), report("b", "S0"), report("c", "S0"))
        assertEquals("S0", summary.severity)
        assertFalse(summary.isConflicted)
    }

    @Test
    fun `two distant voices do not — Rule B counts proximate observers only`() {
        // Distance is measured from the *anchor*, which is the centroid of the
        // flood_reports. So the far voices here are confirms/disputes: were they reports
        // they would drag the centroid toward themselves and become the proximate ones,
        // which is correct behaviour for a feature that has genuinely moved, and would
        // quietly make this test assert nothing.
        val summary = summarize(
            report("a", "S3"),
            report("b", "S0", lat = farLat, type = "dispute", id = "far-1"),
            report("c", "S0", lat = farLat, type = "dispute", id = "far-2"),
        )
        // Their weight still counts toward the disagreement — they are heard, just not
        // trusted to lower a road they are 900 m away from.
        assertEquals("SX", summary.severity)
        assertTrue(summary.isConflicted)
    }

    // ---- Rule C: disagreement is shown, never averaged ----

    @Test
    fun `SX is never a severity in the ladder`() {
        val summary = summarize(report("a", "S3"), report("b", "S0"))
        assertEquals("SX", summary.severity)
        // Rendering must treat it as its own thing, not as a tier between S0 and S3.
        assertEquals(0, severityOrdinal("SX"))
        assertEquals(0.0, summary.confidence, 1e-9)
        assertEquals("unverified", summary.bucket)
    }

    @Test
    fun `agreement at two adjacent danger tiers is not a conflict`() {
        // S2 and S3 are both "danger"; there is no safe-side weight, so nothing conflicts.
        val summary = summarize(report("a", "S3"), report("b", "S2"))
        assertFalse(summary.isConflicted)
        assertEquals("S3", summary.severity)
    }

    // ---- weighting ----

    @Test
    fun `one author's repeated reports do not stack into consensus`() {
        // Same author, three times. Only the latest counts, so this stays a single
        // observer and cannot climb out of Unverified.
        val summary = summarize(
            report("a", "S2", minutesAgo = 30),
            report("a", "S2", minutesAgo = 20),
            report("a", "S2", minutesAgo = 10),
        )
        assertEquals("S2", summary.severity)
        assertEquals("unverified", summary.bucket)
    }

    @Test
    fun `a confirm's own coordinates do not drag the feature anchor`() {
        val far = report("b", "S2", lat = farLat, type = "confirm")
        val summary = summarize(report("a", "S2"), far)
        assertEquals(anchorLat, summary.lat, 1e-6)
        assertEquals(anchorLon, summary.lon, 1e-6)
    }

    @Test
    fun `a responder outweighs a resident when they disagree at the same distance`() {
        // Both live, both proximate, adjacent tiers so Rule C does not fire. Rule A puts
        // the ceiling at S3 either way; what is asserted here is that the responder's
        // agreement raises confidence above what two residents split would give.
        val split = summarize(report("a", "S3"), report("b", "S2"))
        val backed = summarize(report("a", "S3"), report("b", "S2"), report("c", "S3", role = "responder"))
        assertTrue(
            "a responder agreeing should raise confidence (${split.confidence} -> ${backed.confidence})",
            backed.confidence > split.confidence,
        )
    }

    // ---- buckets ----

    @Test
    fun `a single report is never more than unverified`() {
        val summary = summarize(report("a", "S2"))
        assertEquals("unverified", summary.bucket)
    }

    @Test
    fun `agreeing proximate reports reach confirmed`() {
        val summary = summarize(
            report("a", "S2"),
            report("b", "S2"),
            report("c", "S2"),
            report("d", "S2"),
        )
        assertEquals("S2", summary.severity)
        assertEquals("confirmed", summary.bucket)
        assertTrue(summary.confidence >= 0.65)
    }

    @Test
    fun `confidence never leaves the unit interval`() {
        val cases = listOf(
            summarize(report("a", "S2")),
            summarize(report("a", "S3"), report("b", "S0")),
            summarize(report("a", "S1"), report("b", "S1"), report("c", "S1")),
            summarize(report("a", "S3", minutesAgo = 5000)),
        )
        cases.forEach { assertTrue("${it.confidence}", it.confidence in 0.0..1.0) }
    }

    // ---- counts and staleness the UI renders directly ----

    @Test
    fun `confirm and dispute counts are of events, not of authors`() {
        val summary = summarize(
            report("a", "S2"),
            report("b", "S2", type = "confirm", id = "c1"),
            report("c", "S2", type = "confirm", id = "c2"),
            report("d", "S0", type = "dispute", id = "d1"),
        )
        assertEquals(2, summary.confirmCount)
        assertEquals(1, summary.disputeCount)
    }

    @Test
    fun `a feature is stale only once every event has expired`() {
        // S3 lives 360 min. One report 400 min old is expired; a fresh one is not.
        assertTrue(summarize(report("a", "S3", minutesAgo = 400)).isStale)
        assertFalse(summarize(report("a", "S3", minutesAgo = 400), report("b", "S3")).isStale)
    }

    @Test
    fun `history is newest first — the detail sheet renders it in order`() {
        val summary = summarize(
            report("a", "S2", minutesAgo = 30),
            report("b", "S2", minutesAgo = 5),
            report("c", "S2", minutesAgo = 60),
        )
        val times = summary.events.map { it.timestampMs }
        assertEquals(times.sortedDescending(), times)
    }

    // ---- Rule D's boundary with the crowd (the rest is OfficialOverrideTest) ----

    @Test
    fun `an official ruling is not also counted inside the crowd it overrides`() {
        // The day-10 bug: a lone official clearance over a single resident report used
        // to render SX — the official arguing with the residents at role weight 5.
        val summary = summarize(report("a", "S3"), report("kag", "S0", role = "official"))
        assertFalse(summary.isConflicted)
        assertEquals("S0", summary.severity)
        assertEquals("official", summary.bucket)
    }

    // ---- RETENTION_AFTER_EXPIRY_MS grace period (24h) ----

    @Test
    fun `RETENTION_AFTER_EXPIRY_MS is 24 hours`() {
        assertEquals(24L * 60 * 60 * 1000, EventRepository.RETENTION_AFTER_EXPIRY_MS)
    }

    @Test
    fun `expired event within 24h grace period still renders as stale`() {
        // S3 TTL = 360 min. Event from 400 min ago is expired (40 min past expiry).
        // Within the 24h grace period, it must still render as stale so the grey
        // "Luma na — kailangang tingnan" marker shows, prompting someone to check.
        val summary = summarize(report("a", "S3", minutesAgo = 400))
        assertTrue(summary.isStale)
        assertEquals("S3", summary.severity)
    }

    @Test
    fun `expired event far past grace period would be deleted — not tested here`() {
        // Deletion is tested at DAO level (EventDao.deleteExpiredBefore).
        // This test exists as a placeholder: the grace period is the gap between
        // expiry (isStale = true) and deletion (row removed).
        // At 25h past expiry, the event would be deleted on next cold start.
        // We assert the constant is correct above; the deletion logic is integration-tested.
        assertTrue(true)
    }
}
