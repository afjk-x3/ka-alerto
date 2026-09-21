package com.macci.kaalerto.data

import com.macci.kaalerto.identity.LocalIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Item 6: a withdrawal is an event that cancels its own author's earlier events, and only theirs. */
class WithdrawTest {

    private val now = 1_700_000_000_000L
    private val ref = "wz0j1abc"

    private fun ev(author: String, type: String, minutesAgo: Long, severity: String? = "S3", role: String = "resident") = Event(
        id = "$author-$type-$minutesAgo", type = type, lat = 18.1709, lon = 120.6058, featureRef = ref,
        severity = severity, waterLevel = null, authorId = author, authorName = author, authorRole = role,
        timestampMs = now - minutesAgo * 60_000, expiresAt = now + 3_600_000, origin = "local", hopCount = 0, note = null,
    )

    private fun withdraw(author: String, minutesAgo: Long) = ev(author, TYPE_FLOOD_WITHDRAW, minutesAgo, severity = null)

    private fun summarize(vararg events: Event) = Reducer.summarize(ref, events.toList(), now)

    @Test
    fun `withdrawing a lone report removes the feature`() {
        assertNull(summarize(ev("a", "flood_report", 10), withdraw("a", 5)))
    }

    @Test
    fun `withdrawing one of two reports keeps the other and drops the withdrawn one`() {
        val s = summarize(ev("a", "flood_report", 10), ev("b", "flood_report", 8), withdraw("a", 5))!!
        assertEquals(1, s.events.count { it.type == "flood_report" })
        assertEquals("b", s.events.first { it.type == "flood_report" }.authorId)
    }

    @Test
    fun `withdrawing also takes back the author's confirms`() {
        val s = summarize(ev("a", "flood_report", 10), ev("b", "confirm", 8), withdraw("b", 5))!!
        assertEquals(0, s.confirmCount)
    }

    @Test
    fun `someone else's withdrawal changes nothing`() {
        val s = summarize(ev("a", "flood_report", 10), withdraw("intruder", 5))!!
        assertEquals(1, s.events.count { it.type == "flood_report" })
    }

    @Test
    fun `a report filed after the withdrawal counts again`() {
        val s = summarize(ev("a", "flood_report", 10), withdraw("a", 8), ev("a", "flood_report", 2))
        assertNotNull(s)
    }

    @Test
    fun `arrival order does not matter`() {
        val a = ev("a", "flood_report", 10); val b = ev("b", "flood_report", 8); val w = withdraw("a", 5)
        assertEquals(summarize(a, b, w)!!.copy(events = emptyList()), summarize(w, b, a)!!.copy(events = emptyList()))
    }

    @Test
    fun `the timeline keeps the withdrawal but not the withdrawn report`() {
        val s = summarize(ev("a", "flood_report", 10), ev("b", "flood_report", 8), withdraw("a", 5))!!
        assertTrue(s.events.any { it.type == TYPE_FLOOD_WITHDRAW })
        assertFalse(s.events.any { it.authorId == "a" && it.type == "flood_report" })
    }

    @Test
    fun `an official's own withdrawal removes their ruling`() {
        val s = summarize(
            ev("r", "flood_report", 10, "S3"),
            ev("o", "official_status", 8, "S0", role = "official"),
            withdraw("o", 5),
        )!!
        assertNull(s.officialSeverity)
    }

    @Test
    fun `the withdrawal outlives the events it cancels`() {
        val id = LocalIdentity.Identity(authorId = "a", authorName = "a", authorRole = "resident")
        val old = ev("a", "flood_report", 10).copy(expiresAt = now + 10 * 3_600_000)
        val w = withdrawEvent(id, ref, listOf(old), now)
        assertEquals(old.expiresAt, w.expiresAt)
        assertEquals(now + 3_600_000, withdrawEvent(id, ref, emptyList(), now).expiresAt)
    }

    @Test
    fun `canWithdraw only when this device has something live on the spot`() {
        val s = summarize(ev("a", "flood_report", 10), ev("b", "flood_report", 8))!!
        assertTrue(canWithdraw(s, "a"))
        assertFalse(canWithdraw(s, "stranger"))
    }
}
