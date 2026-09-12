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
    fun `a circle invite carries the target authorId and name in its payload, not a flat column`() {
        val event = newCircleInviteEvent(identity("local-a1", "Residente A1B2"), targetAuthorId = "local-b2", targetAuthorName = "Residente B2C3", nowMs = now)

        assertEquals(TYPE_CIRCLE_INVITE, event.type)
        assertEquals("local-a1", event.authorId) // the inviter
        val payload = decodeCircleInvitePayload(event.payload)
        assertEquals("local-b2", payload?.targetAuthorId) // who it's for
        assertEquals("Residente B2C3", payload?.targetAuthorName)
    }

    @Test
    fun `circle events never carry a featureRef or severity, so the flood reducer never sees them`() {
        val checkIn = newCheckInEvent(identity("local-a1", "A"), lat = 18.0, lon = 120.0, nowMs = now)
        val invite = newCircleInviteEvent(identity("local-a1", "A"), targetAuthorId = "local-b2", targetAuthorName = "B", nowMs = now)

        assertTrue(checkIn.featureRef == null && checkIn.severity == null)
        assertTrue(invite.featureRef == null && invite.severity == null)
    }

    @Test
    fun `a check-in and a circle invite never become a map marker`() {
        val checkIn = newCheckInEvent(identity("local-a1", "A"), lat = 18.0, lon = 120.0, nowMs = now)
        val invite = newCircleInviteEvent(identity("local-a1", "A"), targetAuthorId = "local-b2", targetAuthorName = "B", nowMs = now)

        // featureRef == null is what would let the events past the reducer's own
        // grouping by construction — this asserts the reducer actually treats them as
        // invisible, not just that the factory happens to set the right column, mirroring
        // `sos/SosStateTest.kt`'s `an SOS never becomes a map marker`.
        assertTrue(
            com.macci.kaalerto.data.Reducer.summarizeAll(listOf(checkIn, invite), now).isEmpty(),
        )
    }

    @Test
    fun `an invite outlives a check-in, matching how a role outlives an observation`() {
        val checkIn = newCheckInEvent(identity("local-a1", "A"), lat = null, lon = null, nowMs = now)
        val invite = newCircleInviteEvent(identity("local-a1", "A"), targetAuthorId = "local-b2", targetAuthorName = "B", nowMs = now)

        assertTrue("invite must outlive check-in", invite.expiresAt - now > checkIn.expiresAt - now)
    }

    @Test
    fun `decodeCircleInvitePayload returns null for garbage rather than throwing`() {
        assertNull(decodeCircleInvitePayload(null))
        assertNull(decodeCircleInvitePayload("not json"))
        assertNull(decodeCircleInvitePayload("""{"wrong":"shape"}"""))
    }
}
