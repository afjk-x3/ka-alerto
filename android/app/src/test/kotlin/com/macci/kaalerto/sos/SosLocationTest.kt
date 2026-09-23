package com.macci.kaalerto.sos

import com.macci.kaalerto.identity.LocalIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An SOS raised before GPS answers goes out with its location marked unknown, and the
 * requester's phone fills it in afterwards with a location update. Nothing may ever
 * substitute a made-up point for a missing fix.
 */
class SosLocationTest {

    private val now = 1_700_000_000_000L
    private val me = LocalIdentity.Identity("local-me", "Residente A1B2", "resident")
    private val stranger = LocalIdentity.Identity("local-them", "Residente C3D4", "resident")

    @Test
    fun `a request raised with no fix is marked unknown, not placed somewhere`() {
        val request = newSosEvent(me, lat = null, lon = null, accuracyMeters = null, nowMs = now)

        assertFalse(isKnownLocation(request.lat, request.lon))
        val snapshot = SosReducer.snapshot(request.id, listOf(request), me.authorId)
        assertNotNull(snapshot)
        assertFalse(snapshot!!.locationKnown)
        assertNull(snapshot.accuracyMeters)
    }

    @Test
    fun `a request raised with a fix keeps it`() {
        val request = newSosEvent(me, 16.0265, 120.4216, 16f, now)

        val snapshot = SosReducer.snapshot(request.id, listOf(request), me.authorId)!!
        assertTrue(snapshot.locationKnown)
        assertEquals(16.0265, snapshot.lat, 0.0)
        assertEquals(16f, snapshot.accuracyMeters)
    }

    @Test
    fun `the requester's later location update fills in an unknown location`() {
        val request = newSosEvent(me, null, null, null, now)
        val update = sosLocationEvent(request.id, me, 16.0265, 120.4216, 12f, now + 20_000)

        val snapshot = SosReducer.snapshot(request.id, listOf(request, update), me.authorId)!!
        assertTrue(snapshot.locationKnown)
        assertEquals(16.0265, snapshot.lat, 0.0)
        assertEquals(120.4216, snapshot.lon, 0.0)
        assertEquals(12f, snapshot.accuracyMeters)
    }

    @Test
    fun `a location update from anyone but the requester is ignored`() {
        val request = newSosEvent(me, null, null, null, now)
        val forged = sosLocationEvent(request.id, stranger, 14.5995, 120.9842, 5f, now + 20_000)

        val snapshot = SosReducer.snapshot(request.id, listOf(request, forged), me.authorId)!!
        assertFalse(snapshot.locationKnown)
    }

    @Test
    fun `the newest location update wins whatever order the events arrive in`() {
        val request = newSosEvent(me, null, null, null, now)
        val first = sosLocationEvent(request.id, me, 16.0200, 120.4200, 40f, now + 20_000)
        val second = sosLocationEvent(request.id, me, 16.0265, 120.4216, 8f, now + 60_000)
        val events = listOf(request, first, second)

        for (i in events.indices) {
            val rotated = events.drop(i) + events.take(i)
            val snapshot = SosReducer.snapshot(request.id, rotated, me.authorId)!!
            assertEquals("rotation $i", 16.0265, snapshot.lat, 0.0)
            assertEquals("rotation $i", 8f, snapshot.accuracyMeters)
        }
    }

    @Test
    fun `a context amendment does not move the location`() {
        val request = newSosEvent(me, 16.0265, 120.4216, 16f, now)
        val amend = sosAmendEvent(request.id, me, 0.0, 0.0, SosContext(people = "2-4"), now + 5_000)

        val snapshot = SosReducer.snapshot(request.id, listOf(request, amend), me.authorId)!!
        assertEquals(16.0265, snapshot.lat, 0.0)
    }

    @Test
    fun `two requests with unknown locations are never merged into one incident`() {
        val first = newSosEvent(me, null, null, null, now)
        val second = newSosEvent(stranger, null, null, null, now + 1_000)
        val snapshots = listOfNotNull(
            SosReducer.snapshot(first.id, listOf(first), null),
            SosReducer.snapshot(second.id, listOf(second), null),
        )

        val groups = groupNearby(snapshots, SAME_INCIDENT_RADIUS_M)
        assertEquals(2, groups.size)
    }
}
