package com.macci.kaalerto.family

import com.macci.kaalerto.data.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveCircleTest {

    private val now = 1_700_000_000_000L
    private val me = "local-me"

    private fun create(id: String, from: String, fromName: String, circleId: String, name: String, minutesAgo: Long) = Event(
        id = id,
        type = TYPE_CIRCLE_CREATE,
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
        payload = CircleCreatePayload(circleId = circleId, name = name).encode(),
    )

    private fun join(id: String, from: String, fromName: String, circleId: String, minutesAgo: Long) = Event(
        id = id,
        type = TYPE_CIRCLE_JOIN,
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
        payload = CircleJoinPayload(circleId = circleId).encode(),
    )

    @Test
    fun `my own circle_create resolves to a circle of one, with my chosen name`() {
        val events = listOf(create("c1", from = me, fromName = "Me", circleId = "circle-1", name = "Pamilya Reyes", minutesAgo = 5))

        val result = resolveCircle(events, me)

        assertEquals("circle-1", result?.circleId)
        assertEquals("Pamilya Reyes", result?.name)
        assertTrue(result?.members.orEmpty().isEmpty())
    }

    @Test
    fun `joining an existing circle resolves to its name and its other members`() {
        val events = listOf(
            create("c1", from = "local-boy", fromName = "Boy", circleId = "circle-1", name = "Pamilya Reyes", minutesAgo = 20),
            join("j1", from = me, fromName = "Me", circleId = "circle-1", minutesAgo = 5),
        )

        val result = resolveCircle(events, me)

        assertEquals("circle-1", result?.circleId)
        assertEquals("Pamilya Reyes", result?.name)
        assertEquals(listOf("local-boy"), result?.members?.map { it.authorId })
        assertEquals("Boy", result?.members?.single()?.displayName)
    }

    @Test
    fun `joining a circle whose create event hasn't arrived yet resolves with a null name, not no circle at all`() {
        val events = listOf(join("j1", from = me, fromName = "Me", circleId = "circle-1", minutesAgo = 5))

        val result = resolveCircle(events, me)

        assertEquals("circle-1", result?.circleId)
        assertNull(result?.name)
        assertTrue(result?.members.orEmpty().isEmpty())
    }

    @Test
    fun `a device that has never created or joined anything has no circle`() {
        val events = listOf(create("c1", from = "local-boy", fromName = "Boy", circleId = "circle-1", name = "Pamilya Reyes", minutesAgo = 5))

        assertNull(resolveCircle(events, me))
    }

    @Test
    fun `switching circles is authoring a newer join -- the old circle no longer sees me`() {
        val events = listOf(
            create("c1", from = "local-boy", fromName = "Boy", circleId = "circle-1", name = "Circle One", minutesAgo = 30),
            join("j1", from = me, fromName = "Me", circleId = "circle-1", minutesAgo = 20),
            create("c2", from = "local-ana", fromName = "Ana", circleId = "circle-2", name = "Circle Two", minutesAgo = 10),
            join("j2", from = me, fromName = "Me", circleId = "circle-2", minutesAgo = 5),
        )

        val myResult = resolveCircle(events, me)
        val boysResult = resolveCircle(events, "local-boy")

        assertEquals("circle-2", myResult?.circleId)
        assertEquals("Circle Two", myResult?.name)
        assertTrue("I must not still appear in circle-1's membership", boysResult?.members.orEmpty().none { it.authorId == me })
    }

    @Test
    fun `an unrelated circle contributes nothing to my fold`() {
        val events = listOf(
            create("c1", from = "local-a", fromName = "A", circleId = "circle-a", name = "A's Circle", minutesAgo = 20),
            join("j1", from = "local-b", fromName = "B", circleId = "circle-a", minutesAgo = 10),
        )

        assertNull(resolveCircle(events, me))
    }

    @Test
    fun `folding the same events in every rotation resolves the same circle`() {
        val events = listOf(
            create("c1", from = "local-1", fromName = "One", circleId = "circle-1", name = "Test Circle", minutesAgo = 30),
            join("j1", from = "local-2", fromName = "Two", circleId = "circle-1", minutesAgo = 20),
            join("j2", from = "local-3", fromName = "Three", circleId = "circle-1", minutesAgo = 10),
        )
        val expected = resolveCircle(events, "local-1")?.members?.map { it.authorId }?.toSet()

        for (i in events.indices) {
            val rotated = events.drop(i) + events.take(i)
            assertEquals("rotation $i disagreed", expected, resolveCircle(rotated, "local-1")?.members?.map { it.authorId }?.toSet())
        }
    }
}
