package com.macci.kaalerto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Day 10's Rule D and its second-official gate.
 *
 * The gate is the crowd path's safety asymmetry extended to officials: raising a
 * severity takes one voice, lowering a *contradicted* one takes corroboration. These
 * tests pin down exactly where it bites, because the failure mode is silent — a gate
 * that is too eager blocks a legitimate all-clear, and one that is too loose lets a
 * single person overrule people standing in the water.
 */
class OfficialOverrideTest {

    private val now = 1_700_000_000_000L
    private val ref = "wz0j1abc"

    private fun report(
        author: String,
        severity: String,
        role: String = "resident",
        minutesAgo: Long = 5,
        lat: Double = 18.1712,
        lon: Double = 120.5934,
    ) = Event(
        id = "$author-$severity-$minutesAgo",
        type = if (role == "official") "official_status" else "flood_report",
        lat = lat,
        lon = lon,
        featureRef = ref,
        severity = severity,
        waterLevel = null,
        authorId = author,
        authorName = author,
        authorRole = role,
        timestampMs = now - minutesAgo * 60_000,
        expiresAt = now + 60 * 60_000,
        origin = "local",
        hopCount = 0,
        note = null,
    )

    private fun summarize(vararg events: Event) = Reducer.summarize(ref, events.toList(), now)!!

    // ------------------------------------------------------------------ single official

    @Test
    fun `an official raising the severity applies immediately`() {
        val summary = summarize(
            report("res-1", "S1"),
            report("res-2", "S1"),
            report("kagawad-1", "S3", role = "official", minutesAgo = 1),
        )

        // Danger needs less evidence than safety: nothing gates going up.
        assertEquals("S3", summary.severity)
        assertEquals("official", summary.bucket)
        assertFalse(summary.pendingSecondOfficial)
    }

    @Test
    fun `an official clearing a road whose reports have decayed applies immediately`() {
        val summary = summarize(
            // S1's TTL is 120 min, so at 10 hours these have decayed below WEIGHT_FLOOR.
            report("res-1", "S1", minutesAgo = 600),
            report("res-2", "S1", minutesAgo = 590),
            report("kagawad-1", "S0", role = "official", minutesAgo = 1),
        )

        // Nobody is *currently* reporting worse, so there is nobody to overrule — this
        // is the plain "mark cleared" the day 10 DoD wants, and it needs one official.
        assertEquals("S0", summary.severity)
        assertEquals("official", summary.bucket)
        assertFalse(summary.pendingSecondOfficial)
        assertEquals(0, summary.contradictingCount)
    }

    @Test
    fun `two residents still reporting worse do gate a clearance, however old the road is`() {
        val summary = summarize(
            report("res-1", "S2", minutesAgo = 90),
            report("res-2", "S2", minutesAgo = 85),
            report("kagawad-1", "S0", role = "official", minutesAgo = 1),
        )

        // 90 minutes against S2's 240-minute TTL is still live evidence. This case sets
        // how aggressive the gate is: an official standing at a road two people
        // currently call impassable needs a second signature, not a shortcut.
        assertEquals(2, summary.contradictingCount)
        assertTrue(summary.pendingSecondOfficial)
        assertEquals("S2", summary.severity)
    }

    // ------------------------------------------------------------------- the gate bites

    @Test
    fun `one official cannot clear a road two residents say is still impassable`() {
        val summary = summarize(
            report("res-1", "S3", minutesAgo = 4),
            report("res-2", "S3", minutesAgo = 6),
            report("kagawad-1", "S0", role = "official", minutesAgo = 1),
        )

        // OfficialReverse.dc.html: "hindi kayang ibaba ng iisang opisyal ang severity".
        // The crowd's own state stands and the official ruling waits.
        assertEquals("S3", summary.severity)
        assertTrue(summary.pendingSecondOfficial)
        assertEquals("S0", summary.officialSeverity)
        assertEquals(2, summary.contradictingCount)
        assertTrue(summary.bucket != "official")
    }

    @Test
    fun `a second official agreeing releases the gate`() {
        val base = arrayOf(
            report("res-1", "S3", minutesAgo = 4),
            report("res-2", "S3", minutesAgo = 6),
            report("kagawad-1", "S0", role = "official", minutesAgo = 2),
        )

        assertTrue(summarize(*base).pendingSecondOfficial)

        val released = summarize(*base, report("kagawad-2", "S0", role = "official", minutesAgo = 1))

        assertEquals("S0", released.severity)
        assertEquals("official", released.bucket)
        assertFalse(released.pendingSecondOfficial)
    }

    @Test
    fun `the same official posting twice is still one official`() {
        val summary = summarize(
            report("res-1", "S3", minutesAgo = 4),
            report("res-2", "S3", minutesAgo = 6),
            report("kagawad-1", "S0", role = "official", minutesAgo = 3),
            report("kagawad-1", "S0", role = "official", minutesAgo = 1),
        )

        // The gate counts distinct people, not distinct taps. Otherwise it is no gate.
        assertTrue(summary.pendingSecondOfficial)
        assertEquals("S3", summary.severity)
    }

    @Test
    fun `a lone dissenting resident does not gate a clearance`() {
        val summary = summarize(
            report("res-1", "S3", minutesAgo = 4),
            report("kagawad-1", "S0", role = "official", minutesAgo = 1),
        )

        // One voice is enough to *raise* a severity, but not enough to hold an official
        // clearance hostage — the bar is the same DEESCALATION_COUNT the crowd path uses.
        assertEquals("S0", summary.severity)
        assertFalse(summary.pendingSecondOfficial)
        assertEquals(1, summary.contradictingCount)
    }

    // -------------------------------------------------------- reversing another official

    @Test
    fun `reversing another official's clearance takes only one official`() {
        val summary = summarize(
            report("res-1", "S2", minutesAgo = 20),
            report("res-2", "S2", minutesAgo = 18),
            report("kagawad-1", "S0", role = "official", minutesAgo = 10),
            report("kagawad-2", "S3", role = "official", minutesAgo = 1),
        )

        // "Puwede mong baliktarin ang clearance ngayon" — going back up is never gated.
        assertEquals("S3", summary.severity)
        assertEquals("official", summary.bucket)
        assertFalse(summary.pendingSecondOfficial)
    }

    // ------------------------------------------------------------- crowd reports persist

    @Test
    fun `an official status never deletes the resident reports underneath it`() {
        val summary = summarize(
            report("res-1", "S3", minutesAgo = 4),
            report("res-2", "S3", minutesAgo = 6),
            report("kagawad-1", "S3", role = "official", minutesAgo = 1),
        )

        // OfficialVerify.dc.html: "Hindi binubura ng opisyal na status ang mga ito.
        // Makikita pa rin ng residente ang dalawa."
        assertEquals(3, summary.events.size)
        assertTrue(summary.events.any { it.authorId == "res-1" })
        assertTrue(summary.events.any { it.authorId == "res-2" })
    }

    @Test
    fun `with no official at all nothing about the crowd result changes`() {
        val summary = summarize(report("res-1", "S2"), report("res-2", "S2"))

        assertNull(summary.officialSeverity)
        assertFalse(summary.pendingSecondOfficial)
        assertEquals(0, summary.contradictingCount)
        assertEquals("S2", summary.severity)
    }

    @Test
    fun `an SX conflict cannot be cleared by one official but can by two`() {
        // The day 10 DoD's road. Only one voice on the "safe" side: two would satisfy
        // Rule B's de-escalation bar and resolve the disagreement rather than leaving
        // it as SX, which is exactly how the seeded pair is built.
        val conflict = arrayOf(
            report("res-1", "S3", minutesAgo = 3),
            report("res-2", "S3", minutesAgo = 5),
            report("res-3", "S0", minutesAgo = 4),
        )
        assertTrue(summarize(*conflict).isConflicted)

        val oneOfficial = summarize(*conflict, report("kagawad-1", "S0", role = "official", minutesAgo = 1))
        assertTrue(oneOfficial.pendingSecondOfficial)
        assertTrue(oneOfficial.isConflicted)

        val twoOfficials = summarize(
            *conflict,
            report("kagawad-1", "S0", role = "official", minutesAgo = 2),
            report("kagawad-2", "S0", role = "official", minutesAgo = 1),
        )
        assertEquals("S0", twoOfficials.severity)
        assertEquals("official", twoOfficials.bucket)
        assertFalse(twoOfficials.isConflicted)
    }
}
