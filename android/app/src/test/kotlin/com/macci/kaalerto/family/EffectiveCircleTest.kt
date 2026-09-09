package com.macci.kaalerto.family

import com.macci.kaalerto.data.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectiveCircleTest {

    private val now = 1_700_000_000_000L
    private val me = "local-me"

    private fun invite(id: String, from: String, fromName: String, targetAuthorId: String, minutesAgo: Long) = Event(
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
        payload = CircleInvitePayload(targetAuthorId).encode(),
    )

    @Test
    fun `a circle_invite targeting me contributes the inviter`() {
        val events = listOf(invite("i1", from = "local-boy", fromName = "Boy", targetAuthorId = me, minutesAgo = 5))

        val result = effectiveCircle(locallyAdded = emptyList(), allEvents = events, myAuthorId = me)

        assertEquals(listOf("local-boy"), result.map { it.authorId })
        assertEquals("Boy", result.single().displayName)
    }

    @Test
    fun `a circle_invite targeting someone else is dropped`() {
        val events = listOf(invite("i1", from = "local-boy", fromName = "Boy", targetAuthorId = "local-someone-else", minutesAgo = 5))

        val result = effectiveCircle(locallyAdded = emptyList(), allEvents = events, myAuthorId = me)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `two invites from the same inviter still produce exactly one entry`() {
        val events = listOf(
            invite("i1", from = "local-boy", fromName = "Boy", targetAuthorId = me, minutesAgo = 5),
            invite("i2", from = "local-boy", fromName = "Boy", targetAuthorId = me, minutesAgo = 1), // mesh re-delivery, or scanned twice
        )

        val result = effectiveCircle(locallyAdded = emptyList(), allEvents = events, myAuthorId = me)

        assertEquals(1, result.size)
    }

    @Test
    fun `an invite I authored myself never contributes an entry back to my own list`() {
        // I am the one who scanned Boy's QR — I authored this invite, targeting Boy.
        val events = listOf(invite("i1", from = me, fromName = "Me", targetAuthorId = "local-boy", minutesAgo = 5))

        val result = effectiveCircle(locallyAdded = emptyList(), allEvents = events, myAuthorId = me)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `locally-added members are always included even with no invite events`() {
        val locallyAdded = listOf(CircleMember(authorId = "local-boy", displayName = "Boy", pairedAtMs = now))

        val result = effectiveCircle(locallyAdded = locallyAdded, allEvents = emptyList(), myAuthorId = me)

        assertEquals(listOf("local-boy"), result.map { it.authorId })
    }

    @Test
    fun `a member present both locally and via invite appears once`() {
        val locallyAdded = listOf(CircleMember(authorId = "local-boy", displayName = "Boy", pairedAtMs = now))
        val events = listOf(invite("i1", from = "local-boy", fromName = "Boy", targetAuthorId = me, minutesAgo = 5))

        val result = effectiveCircle(locallyAdded, events, me)

        assertEquals(1, result.size)
    }
}
