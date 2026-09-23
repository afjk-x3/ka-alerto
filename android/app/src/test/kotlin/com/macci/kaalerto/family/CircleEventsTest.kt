package com.macci.kaalerto.family

import com.macci.kaalerto.identity.LocalIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleEventsTest {

    private val now = 1_700_000_000_000L

    private fun identity(id: String, name: String) =
        LocalIdentity.Identity(authorId = id, authorName = name, authorRole = LocalIdentity.ROLE_RESIDENT)

    @Test
    fun `a check-in with no location uses the null-island sentinel, same as role events`() {
        val event = newCheckInEvent(identity("local-a1", "Residente A1B2"), lat = null, lon = null, nowMs = now)

        assertEquals(0.0, event.lat, 0.0)
        assertEquals(0.0, event.lon, 0.0)
    }

    @Test
    fun `a check-in with a real location stores it verbatim`() {
        val event = newCheckInEvent(identity("local-a1", "Residente A1B2"), lat = 18.1709, lon = 120.6058, nowMs = now)

        assertEquals(18.1709, event.lat, 0.000001)
        assertEquals(120.6058, event.lon, 0.000001)
    }

    @Test
    fun `a check-in carries no payload and the right type`() {
        val event = newCheckInEvent(identity("local-a1", "Residente A1B2"), lat = null, lon = null, nowMs = now)

        assertEquals(TYPE_CHECKIN, event.type)
        assertNull(event.payload)
        assertEquals("local-a1", event.authorId)
        assertEquals("Residente A1B2", event.authorName)
    }

    @Test
    fun `a circle create event carries its own id and chosen name`() {
        val event = newCircleCreateEvent(identity("local-a1", "Residente A1B2"), circleId = "circle-xyz", name = "Pamilya Reyes", nowMs = now)

        assertEquals(TYPE_CIRCLE_CREATE, event.type)
        assertEquals("local-a1", event.authorId)
        val payload = decodeCircleCreatePayload(event.payload)
        assertEquals("circle-xyz", payload?.circleId)
        assertEquals("Pamilya Reyes", payload?.name)
    }

    @Test
    fun `a circle join event carries only the circleId, not a name`() {
        val event = newCircleJoinEvent(identity("local-b2", "Residente B2C3"), circleId = "circle-xyz", nowMs = now)

        assertEquals(TYPE_CIRCLE_JOIN, event.type)
        assertEquals("local-b2", event.authorId)
        val payload = decodeCircleJoinPayload(event.payload)
        assertEquals("circle-xyz", payload?.circleId)
    }

    @Test
    fun `circle events never carry a featureRef or severity, so the flood reducer never sees them`() {
        val checkIn = newCheckInEvent(identity("local-a1", "A"), lat = 18.0, lon = 120.0, nowMs = now)
        val create = newCircleCreateEvent(identity("local-a1", "A"), circleId = "circle-1", name = "Test", nowMs = now)
        val join = newCircleJoinEvent(identity("local-b2", "B"), circleId = "circle-1", nowMs = now)

        assertTrue(checkIn.featureRef == null && checkIn.severity == null)
        assertTrue(create.featureRef == null && create.severity == null)
        assertTrue(join.featureRef == null && join.severity == null)
    }

    @Test
    fun `a check-in, a circle create and a circle join never become a map marker`() {
        val checkIn = newCheckInEvent(identity("local-a1", "A"), lat = 18.0, lon = 120.0, nowMs = now)
        val create = newCircleCreateEvent(identity("local-a1", "A"), circleId = "circle-1", name = "Test", nowMs = now)
        val join = newCircleJoinEvent(identity("local-b2", "B"), circleId = "circle-1", nowMs = now)

        assertTrue(
            com.macci.kaalerto.data.Reducer.summarizeAll(listOf(checkIn, create, join), now).isEmpty(),
        )
    }

    @Test
    fun `a circle create outlives a check-in, matching how a role outlives an observation`() {
        val checkIn = newCheckInEvent(identity("local-a1", "A"), lat = null, lon = null, nowMs = now)
        val create = newCircleCreateEvent(identity("local-a1", "A"), circleId = "circle-1", name = "Test", nowMs = now)

        assertTrue("create must outlive check-in", create.expiresAt - now > checkIn.expiresAt - now)
    }

    @Test
    fun `decode functions return null for garbage rather than throwing`() {
        assertNull(decodeCircleCreatePayload(null))
        assertNull(decodeCircleCreatePayload("not json"))
        assertNull(decodeCircleCreatePayload("""{"wrong":"shape"}"""))
        assertNull(decodeCircleJoinPayload(null))
        assertNull(decodeCircleJoinPayload("not json"))
    }
}
