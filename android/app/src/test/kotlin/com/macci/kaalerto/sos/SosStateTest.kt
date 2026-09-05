package com.macci.kaalerto.sos

import com.macci.kaalerto.data.Event
import com.macci.kaalerto.identity.LocalIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosStateTest {

    private val now = 1_700_000_000_000L
    private val me = LocalIdentity.Identity("local-me", "Residente A1B2", "resident")
    private val neighbour = LocalIdentity.Identity("local-them", "Residente C3D4", "resident")

    private fun request(identity: LocalIdentity.Identity = me, atMs: Long = now): Event =
        newSosEvent(identity, 18.1712, 120.5934, 6f, atMs)

    // ------------------------------------------------------------------ state machine

    @Test
    fun `every arrow in the architecture diagram moves forward in rank`() {
        // docs/03-architecture.md §6.2. If one of these ever goes backwards, monotonic
        // merging would silently drop a legitimate transition instead of applying it.
        val arrows = listOf(
            SosState.DRAFT to SosState.QUEUED,
            SosState.QUEUED to SosState.BEACONING,
            SosState.QUEUED to SosState.UNREACHABLE,
            SosState.QUEUED to SosState.DELIVERED,
            SosState.BEACONING to SosState.RELAYED,
            SosState.BEACONING to SosState.UNREACHABLE,
            SosState.UNREACHABLE to SosState.RELAYED,
            SosState.UNREACHABLE to SosState.DELIVERED,
            SosState.RELAYED to SosState.DELIVERED,
            SosState.RELAYED to SosState.ACKNOWLEDGED,
            SosState.DELIVERED to SosState.ACKNOWLEDGED,
            SosState.ACKNOWLEDGED to SosState.EN_ROUTE,
            SosState.EN_ROUTE to SosState.ON_SCENE,
            SosState.ON_SCENE to SosState.RESCUED,
        )
        arrows.forEach { (from, to) ->
            assertTrue("$from -> $to must increase rank", to.rank > from.rank)
            assertEquals("$from -> $to must be applied", to, mergeSosState(from, to))
        }
    }

    @Test
    fun `a late lower state cannot regress a higher one`() {
        // The mesh makes no ordering promise, so a BEACONING written on this phone can
        // arrive at a peer after an ACKNOWLEDGED relayed from a responder.
        assertEquals(SosState.ACKNOWLEDGED, mergeSosState(SosState.ACKNOWLEDGED, SosState.BEACONING))
        assertEquals(SosState.RELAYED, mergeSosState(SosState.RELAYED, SosState.QUEUED))
    }

    @Test
    fun `marking safe wins over any progress state, and nothing reopens it`() {
        assertEquals(SosState.SAFE_SELF_RESOLVED, mergeSosState(SosState.EN_ROUTE, SosState.SAFE_SELF_RESOLVED))
        assertEquals(SosState.SAFE_SELF_RESOLVED, mergeSosState(SosState.SAFE_SELF_RESOLVED, SosState.ACKNOWLEDGED))
        assertEquals(SosState.CANCELLED, mergeSosState(SosState.CANCELLED, SosState.RELAYED))
    }

    @Test
    fun `only the states this build can actually drive are marked reachable`() {
        // Guards the honesty claim on the status screen: if a later day makes RELAYED
        // real, this test is the thing that says to update the flag with it.
        assertTrue(SosState.BEACONING.reachableInThisBuild)
        assertTrue(SosState.UNREACHABLE.reachableInThisBuild)
        assertTrue(!SosState.RELAYED.reachableInThisBuild)
        assertTrue(!SosState.DELIVERED.reachableInThisBuild)
        assertTrue(!SosState.ACKNOWLEDGED.reachableInThisBuild)
    }

    // ---------------------------------------------------------------------- reducer

    @Test
    fun `a bare request folds to QUEUED with no context`() {
        val sos = request()
        val snapshot = SosReducer.snapshot(sos.id, listOf(sos), me.authorId)

        assertNotNull(snapshot)
        assertEquals(SosState.QUEUED, snapshot!!.state)
        assertTrue(snapshot.context.isEmpty)
        assertTrue(snapshot.isMine)
        assertEquals(6f, snapshot.accuracyMeters)
    }

    @Test
    fun `amendments merge field by field instead of replacing`() {
        val sos = request()
        val first = sosAmendEvent(sos.id, me, sos.lat, sos.lon, SosContext(people = "5–8"), now + 5_000)
        val second = sosAmendEvent(sos.id, me, sos.lat, sos.lon, SosContext(water = "Dibdib"), now + 9_000)

        val snapshot = SosReducer.snapshot(sos.id, listOf(sos, first, second), me.authorId)!!

        // The water answer must not erase the people count sent four seconds earlier.
        assertEquals("5–8", snapshot.context.people)
        assertEquals("Dibdib", snapshot.context.water)
    }

    @Test
    fun `state events out of order still fold to the same answer`() {
        val sos = request()
        val beaconing = sosStateEvent(sos.id, me, sos.lat, sos.lon, SosState.BEACONING, now + 1_000)
        val unreachable = sosStateEvent(sos.id, me, sos.lat, sos.lon, SosState.UNREACHABLE, now + 30_000)

        val inOrder = SosReducer.snapshot(sos.id, listOf(sos, beaconing, unreachable), me.authorId)!!
        val reversed = SosReducer.snapshot(sos.id, listOf(unreachable, beaconing, sos), me.authorId)!!

        // NFR-4: two devices holding the same events must display the same status.
        assertEquals(SosState.UNREACHABLE, inOrder.state)
        assertEquals(inOrder.state, reversed.state)
    }

    @Test
    fun `two concurrent requests do not bleed into each other`() {
        val mine = request()
        val theirs = request(neighbour, now + 1_000)
        val amendTheirs = sosAmendEvent(theirs.id, neighbour, theirs.lat, theirs.lon, SosContext(people = "9+"), now + 2_000)
        val all = listOf(mine, theirs, amendTheirs)

        assertTrue(SosReducer.snapshot(mine.id, all, me.authorId)!!.context.isEmpty)
        assertEquals("9+", SosReducer.snapshot(theirs.id, all, me.authorId)!!.context.people)
    }

    @Test
    fun `activeMine ignores a neighbour's request and a closed one of my own`() {
        val theirs = request(neighbour)
        val mine = request(me, now + 1_000)
        val safe = sosStateEvent(mine.id, me, mine.lat, mine.lon, SosState.SAFE_SELF_RESOLVED, now + 60_000)

        // A relayed-in neighbour's SOS is somebody else's emergency — it must never
        // become the banner telling *you* that help is being called for you.
        assertEquals(mine.id, SosReducer.activeMine(listOf(theirs, mine), me.authorId)?.sosId)
        assertNull(SosReducer.activeMine(listOf(theirs, mine, safe), me.authorId))
    }

    @Test
    fun `an SOS never becomes a map marker`() {
        val sos = request()
        val amend = sosAmendEvent(sos.id, me, sos.lat, sos.lon, SosContext(people = "1"), now + 1_000)

        // featureRef is what day 4's Reducer groups on. A non-null one here would put a
        // flood severity marker on the requester's house.
        assertNull(sos.featureRef)
        assertNull(amend.featureRef)
        assertTrue(com.macci.kaalerto.data.Reducer.summarizeAll(listOf(sos, amend), now).isEmpty())
    }

    @Test
    fun `an SOS outlives every flood report so a late rescuer still finds it`() {
        val sos = request()
        // docs/03-architecture.md §6.4.1 — the SOS does not expire just because nobody
        // was listening yet. S3, the longest flood TTL, is 6 hours.
        val longestFloodTtlMs = com.macci.kaalerto.data.ttlMinutesFor("S3") * 60_000L
        assertTrue(sos.expiresAt - sos.timestampMs > longestFloodTtlMs)
    }

    // ----------------------------------------------------------------------- context

    @Test
    fun `medical none is exclusive in both directions`() {
        var medical = SosContext.toggleMedical(emptyList(), "Sugatan")
        medical = SosContext.toggleMedical(medical, "Buntis")
        assertEquals(listOf("Sugatan", "Buntis"), medical)

        // Saying "wala" clears an actual need rather than sitting alongside it.
        medical = SosContext.toggleMedical(medical, SosContext.MEDICAL_NONE)
        assertEquals(listOf(SosContext.MEDICAL_NONE), medical)

        medical = SosContext.toggleMedical(medical, "Gamot sa puso")
        assertEquals(listOf("Gamot sa puso"), medical)
    }
}
