package com.macci.kaalerto.family

import com.macci.kaalerto.data.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectiveCircleTest {

    private val now = 1_700_000_000_000L
    private val me = "local-me"

    private fun invite(
        id: String,
        from: String,
        fromName: String,
        targetAuthorId: String,
        targetAuthorName: String,
        minutesAgo: Long,
    ) = Event(
        id = id,
        type = TYPE_CIRCLE_INVITE,
        lat = 0.0,
        lon = 0.0,
        featureRef = null,
        severity = null,
        waterLevel = null,
        authorId = from,
        authorName = fromName,
        authorRole = "resident",
        timestampMs = now - minutesAgo * 60_000,
        expiresAt = now + 1_000_000_000L,
        origin = "local",
        hopCount = 0,
        note = null,
        payload = CircleInvitePayload(targetAuthorId = targetAuthorId, targetAuthorName = targetAuthorName).encode(),
    )

    @Test
    fun `a circle_invite targeting me contributes the inviter`() {
        val events = listOf(invite("i1", from = "local-boy", fromName = "Boy", targetAuthorId = me, targetAuthorName = "Me", minutesAgo = 5))

        val result = effectiveCircle(events, me)

        assertEquals(listOf("local-boy"), result.map { it.authorId })
        assertEquals("Boy", result.single().displayName)
    }

    @Test
    fun `an invite I authored myself adds the person I scanned to my own circle`() {
        // I am the one who scanned Boy's QR — I authored this invite, targeting Boy.
        // Unlike the pre-graph design, this must show up immediately: my own scan is
        // an edge in the graph too, not just a one-way notice sent to Boy.
        val events = listOf(invite("i1", from = me, fromName = "Me", targetAuthorId = "local-boy", targetAuthorName = "Boy", minutesAgo = 5))

        val result = effectiveCircle(events, me)

        assertEquals(listOf("local-boy"), result.map { it.authorId })
        assertEquals("Boy", result.single().displayName)
    }

    @Test
    fun `a circle_invite naming neither me nor anyone connected to me contributes nothing`() {
        val events = listOf(invite("i1", from = "local-stranger-a", fromName = "A", targetAuthorId = "local-stranger-b", targetAuthorName = "B", minutesAgo = 5))

        val result = effectiveCircle(events, me)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `two invites for the same edge still produce exactly one entry`() {
        val events = listOf(
            invite("i1", from = "local-boy", fromName = "Boy", targetAuthorId = me, targetAuthorName = "Me", minutesAgo = 5),
            invite("i2", from = "local-boy", fromName = "Boy", targetAuthorId = me, targetAuthorName = "Me", minutesAgo = 1), // mesh re-delivery, or scanned twice
        )

        val result = effectiveCircle(events, me)

        assertEquals(1, result.size)
    }

    @Test
    fun `scanning either of two already-paired members joins the same circle as both`() {
        // The exact scenario the bug report named: 1 and 2 are already paired.
        // 3 scans 1 (not 2) — 3 must still end up with both.
        val events = listOf(
            invite("i1", from = "local-1", fromName = "One", targetAuthorId = "local-2", targetAuthorName = "Two", minutesAgo = 20),
            invite("i2", from = "local-3", fromName = "Three", targetAuthorId = "local-1", targetAuthorName = "One", minutesAgo = 5),
        )

        val circleOf3 = effectiveCircle(events, "local-3")

        assertEquals(setOf("local-1", "local-2"), circleOf3.map { it.authorId }.toSet())
    }

    @Test
    fun `scanning the other already-paired member reaches the same circle either way`() {
        // Same edges as above, but 3 scans 2 instead of 1 — the result must not depend
        // on which of the two existing members got scanned.
        val events = listOf(
            invite("i1", from = "local-1", fromName = "One", targetAuthorId = "local-2", targetAuthorName = "Two", minutesAgo = 20),
            invite("i2", from = "local-3", fromName = "Three", targetAuthorId = "local-2", targetAuthorName = "Two", minutesAgo = 5),
        )

        val circleOf3 = effectiveCircle(events, "local-3")

        assertEquals(setOf("local-1", "local-2"), circleOf3.map { it.authorId }.toSet())
    }

    @Test
    fun `every member of a fully-connected circle agrees on its membership`() {
        val events = listOf(
            invite("i1", from = "local-1", fromName = "One", targetAuthorId = "local-2", targetAuthorName = "Two", minutesAgo = 20),
            invite("i2", from = "local-3", fromName = "Three", targetAuthorId = "local-1", targetAuthorName = "One", minutesAgo = 5),
        )

        val circleOf1 = effectiveCircle(events, "local-1").map { it.authorId }.toSet()
        val circleOf2 = effectiveCircle(events, "local-2").map { it.authorId }.toSet()
        val circleOf3 = effectiveCircle(events, "local-3").map { it.authorId }.toSet()

        assertEquals(setOf("local-2", "local-3"), circleOf1)
        assertEquals(setOf("local-1", "local-3"), circleOf2)
        assertEquals(setOf("local-1", "local-2"), circleOf3)
    }

    @Test
    fun `folding the same edges in every rotation produces the same circle`() {
        val events = listOf(
            invite("i1", from = "local-1", fromName = "One", targetAuthorId = "local-2", targetAuthorName = "Two", minutesAgo = 30),
            invite("i2", from = "local-3", fromName = "Three", targetAuthorId = "local-1", targetAuthorName = "One", minutesAgo = 20),
            invite("i3", from = "local-4", fromName = "Four", targetAuthorId = "local-2", targetAuthorName = "Two", minutesAgo = 10),
        )
        val expected = effectiveCircle(events, "local-1").map { it.authorId }.toSet()

        for (i in events.indices) {
            val rotated = events.drop(i) + events.take(i)
            assertEquals("rotation $i disagreed", expected, effectiveCircle(rotated, "local-1").map { it.authorId }.toSet())
        }
    }

    @Test
    fun `a member reachable only transitively still gets a real display name`() {
        // "me" only ever scanned 1; 1 and 2 paired independently, with no event
        // authored by 2 anywhere in the log. 2's name must still come through, from
        // the targetAuthorName on the edge that first mentions them.
        val events = listOf(
            invite("i1", from = "local-1", fromName = "One", targetAuthorId = "local-2", targetAuthorName = "Two", minutesAgo = 20),
            invite("i2", from = me, fromName = "Me", targetAuthorId = "local-1", targetAuthorName = "One", minutesAgo = 5),
        )

        val result = effectiveCircle(events, me)

        assertEquals("Two", result.single { it.authorId == "local-2" }.displayName)
    }
}
