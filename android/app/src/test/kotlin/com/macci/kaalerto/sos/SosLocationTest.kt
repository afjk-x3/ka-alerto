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

    @Test
    fun `a spot picked on the map places the request and says so`() {
        val request = newSosEvent(me, null, null, null, now)
        val picked = sosLocationEvent(request.id, me, 16.0265, 120.4216, null, now + 20_000, picked = true)

        val snapshot = SosReducer.snapshot(request.id, listOf(request, picked), me.authorId)!!
        assertEquals(SosLocationSource.PICKED, snapshot.locationSource)
        assertTrue(snapshot.locationKnown)
    }

    @Test
    fun `a relay's position is used only while the requester has given none`() {
        val request = newSosEvent(me, null, null, null, now)
        val relay = sosHeardNearEvent(request.id, stranger, 16.0270, 120.4220, 10f, now + 10_000)

        val approx = SosReducer.snapshot(request.id, listOf(request, relay), stranger.authorId)!!
        assertEquals(SosLocationSource.RELAY, approx.locationSource)
        assertEquals(16.0270, approx.lat, 0.0)

        val gps = sosLocationEvent(request.id, me, 16.0265, 120.4216, 8f, now + 30_000)
        val fixed = SosReducer.snapshot(request.id, listOf(request, relay, gps), stranger.authorId)!!
        assertEquals(SosLocationSource.GPS, fixed.locationSource)
        assertEquals(16.0265, fixed.lat, 0.0)
    }

    @Test
    fun `the requester cannot pass off a position as a relay's`() {
        val request = newSosEvent(me, null, null, null, now)
        val selfRelay = sosHeardNearEvent(request.id, me, 16.0270, 120.4220, 10f, now + 10_000)

        val snapshot = SosReducer.snapshot(request.id, listOf(request, selfRelay), me.authorId)!!
        assertEquals(SosLocationSource.NONE, snapshot.locationSource)
    }

    @Test
    fun `only a phone one hop from the requester answers with its position`() {
        val request = newSosEvent(me, null, null, null, now)
        fun heardAt(hops: Int) = SosReducer.snapshot(
            request.id,
            listOf(request.copy(origin = "mesh", hopCount = hops)),
            stranger.authorId,
        )!!

        assertEquals(listOf(request.id), requestsNeedingHeardNear(listOf(heardAt(1))))
        assertTrue(requestsNeedingHeardNear(listOf(heardAt(2))).isEmpty())
        // Via Supabase: no radio range to speak of.
        val viaCloud = SosReducer.snapshot(request.id, listOf(request.copy(origin = "server")), stranger.authorId)!!
        assertTrue(requestsNeedingHeardNear(listOf(viaCloud)).isEmpty())
    }

    @Test
    fun `the home barangay rides with the request as a hint`() {
        val request = newSosEvent(me, null, null, null, now, homeBarangay = "Brgy. Poblacion, Mapandan")

        val snapshot = SosReducer.snapshot(request.id, listOf(request), me.authorId)!!
        assertEquals("Brgy. Poblacion, Mapandan", snapshot.homeBarangay)
        assertFalse(snapshot.locationKnown)
    }
}
