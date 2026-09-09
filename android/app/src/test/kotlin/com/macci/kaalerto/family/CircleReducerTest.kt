package com.macci.kaalerto.family

import com.macci.kaalerto.data.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CircleReducerTest {

    private val now = 1_700_000_000_000L

    private fun checkIn(id: String, authorId: String, authorName: String, minutesAgo: Long) = Event(
        id = id,
        type = TYPE_CHECKIN,
        lat = 0.0,
        lon = 0.0,
        featureRef = null,
        severity = null,
        waterLevel = null,
        authorId = authorId,
        authorName = authorName,
        authorRole = "resident",
        timestampMs = now - minutesAgo * 60_000,
        expiresAt = now + CHECKIN_TTL_MS,
        origin = "local",
        hopCount = 0,
        note = null,
    )

    private val boy = CircleMember(authorId = "local-boy", displayName = "Boy", pairedAtMs = now)
    private val maria = CircleMember(authorId = "local-maria", displayName = "Maria", pairedAtMs = now)

    @Test
    fun `a member with no check-in events reads as no check-in yet`() {
        val result = circleStatuses(allEvents = emptyList(), circle = listOf(boy))

        assertEquals(1, result.size)
        assertNull(result.single().lastCheckInMs)
    }

    @Test
    fun `the latest check-in per member wins`() {
        val events = listOf(
            checkIn("c1", "local-boy", "Boy", minutesAgo = 30),
            checkIn("c2", "local-boy", "Boy", minutesAgo = 5),
        )

        val result = circleStatuses(events, listOf(boy))

        assertEquals(now - 5 * 60_000, result.single().lastCheckInMs)
    }

    @Test
    fun `a check-in from someone not in the circle is ignored`() {
        val events = listOf(checkIn("c1", "local-stranger", "Stranger", minutesAgo = 5))

        val result = circleStatuses(events, listOf(boy))

        assertNull(result.single().lastCheckInMs)
    }

    @Test
    fun `every paired member appears even with zero matching events — absence is not the same claim as presence`() {
        val result = circleStatuses(allEvents = emptyList(), circle = listOf(boy, maria))

        assertEquals(setOf("local-boy", "local-maria"), result.map { it.authorId }.toSet())
    }

    @Test
    fun `the fold is independent of delivery order`() {
        val events = listOf(
            checkIn("c1", "local-boy", "Boy", minutesAgo = 40),
            checkIn("c2", "local-maria", "Maria", minutesAgo = 20),
            checkIn("c3", "local-boy", "Boy", minutesAgo = 10),
        )
        val expected = circleStatuses(events, listOf(boy, maria))

        for (i in events.indices) {
            val rotated = events.drop(i) + events.take(i)
            assertEquals("rotation $i disagreed", expected, circleStatuses(rotated, listOf(boy, maria)))
        }
    }
}
