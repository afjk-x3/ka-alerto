package com.macci.kaalerto.advisory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Real PAGASA files fetched 24 Sep 2026 (src/test/resources/advisory). */
class AdvisoriesTest {

    private fun fixture(name: String) = File("src/test/resources/advisory/$name").readText()
    private val now = java.time.OffsetDateTime.parse("2026-09-24T18:00:00+08:00").toInstant().toEpochMilli()

    @Test
    fun `the feed lists entries with their CAP links`() {
        val entries = parseFeed(fixture("pagasa-feed.xml"))
        assertEquals(51, entries.size)
        assertTrue(entries.all { it.capUrl.endsWith(".cap") })
    }

    @Test
    fun `a flood advisory keeps PAGASA's own words and areas`() {
        val gfa = parseCap(fixture("gfa.cap"))!!
        assertEquals("1e110330-a2a1-4e53-a6a2-2223bb26cce0", gfa.capId)
        assertEquals("General Flood Advisory (Moderate)", gfa.event)
        assertTrue(gfa.covers("South Cotabato"))
        assertFalse(gfa.covers("Pangasinan"))
        assertTrue(gfa.description.startsWith("Under present weather conditions"))
    }

    @Test
    fun `every phone writes the same event for the same alert`() {
        val gfa = parseCap(fixture("gfa.cap"))!!
        assertEquals(advisoryEvent(gfa), advisoryEvent(parseCap(fixture("gfa.cap"))!!))
        assertEquals("pagasa-${gfa.capId}", advisoryEvent(gfa)!!.id)
    }

    @Test
    fun `a cancellation hides the alert it references`() {
        val cancel = parseCap(fixture("gfa-cancel.cap"))!!
        assertEquals("Cancel", cancel.msgType)
        val cancelledId = cancel.references.single()
        val original = parseCap(fixture("gfa.cap"))!!.copy(capId = cancelledId)
        val events = listOfNotNull(advisoryEvent(original))

        assertEquals(1, activeAdvisories(events, now, "South Cotabato").size)
        assertTrue(activeAdvisories(events + listOfNotNull(advisoryEvent(cancel)), now, "South Cotabato").isEmpty())
    }

    @Test
    fun `an update replaces the alert it references`() {
        val original = parseCap(fixture("gfa.cap"))!!
        val update = original.copy(capId = "update-1", msgType = "Update", references = listOf(original.capId))
        val events = listOfNotNull(advisoryEvent(original), advisoryEvent(update))

        assertEquals(listOf("update-1"), activeAdvisories(events, now, "South Cotabato").map { it.capId })
    }

    @Test
    fun `a nationwide tropical cyclone alert counts for every province`() {
        val tca = parseCap(fixture("gfa.cap"))!!.copy(areas = listOf("Philippine Area of Responsibility"))
        assertTrue(tca.covers("Pangasinan"))
    }

    @Test
    fun `an alert for another province is not shown`() {
        val events = listOfNotNull(advisoryEvent(parseCap(fixture("gfa.cap"))!!))
        assertNotNull(events.single())
        assertTrue(activeAdvisories(events, now, "Pangasinan").isEmpty())
    }
}
