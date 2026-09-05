package com.macci.kaalerto.sos

import com.macci.kaalerto.data.Event
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.mesh.acceptForStore
import com.macci.kaalerto.mesh.relayable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Day 9 — the acknowledgement round trip and the privacy policy that governs what an
 * SOS is allowed to carry when it leaves the device.
 *
 * The radio half still needs two phones. What is checked here is everything the radio
 * would carry: that the ack survives the trip, that it lands monotonically whatever
 * order it arrives in, and that the fields §6.5 wants encrypted are simply not there.
 */
class SosAckTest {

    private val now = 1_700_000_000_000L
    private val requester = LocalIdentity.Identity("local-requester", "Maria S.", LocalIdentity.ROLE_RESIDENT)
    private val responder = LocalIdentity.Identity("local-responder", "Boy R.", LocalIdentity.ROLE_RESPONDER)

    private fun request(context: SosContext = SosContext()): Event {
        val event = newSosEvent(requester, 18.1712, 120.5934, 5f, now)
        if (context.isEmpty) return event
        val payload = decodeSosPayload(event.payload)!!.copy(context = context)
        return event.copy(payload = payload.encode())
    }

    private val fullContext = SosContext(
        people = "5–8",
        companions = listOf("Bata"),
        medical = listOf("Gamot sa puso"),
        water = "Dibdib",
        trend = "Tumataas",
    )

    // ------------------------------------------------------------------- mesh policy

    @Test
    fun `medical detail and the requester's name never leave the device`() {
        val sent = redactForMesh(request(fullContext))
        val context = decodeSosPayload(sent.payload)!!.context!!

        // docs/03-architecture.md 6.5 wants this encrypted. With no crypto, the honest
        // equivalent is not to send it: what is not sent cannot be read off a relay.
        assertTrue(context.medical.isEmpty())
        assertEquals(REDACTED_AUTHOR, sent.authorName)
        assertTrue(!sent.payload!!.contains("Gamot sa puso"))
        assertTrue(!sent.payload!!.contains("Maria"))
    }

    @Test
    fun `what a responder actually needs still travels`() {
        val context = decodeSosPayload(redactForMesh(request(fullContext)).payload)!!.context!!

        // QueueVolunteer.dc.html's own footer: a registered volunteer gets "lokasyon at
        // bilang ng tao". Strip those too and the queue screen is useless.
        assertEquals("5–8", context.people)
        assertEquals(listOf("Bata"), context.companions)
        assertEquals("Dibdib", context.water)
        assertEquals("Tumataas", context.trend)
    }

    @Test
    fun `redaction survives being relayed onward`() {
        // B redacts before sending to C. C stores what it was given and re-shares that,
        // so the detail is gone irreversibly at the first hop rather than at each one.
        val onB = acceptForStore(listOf(redactForMesh(request(fullContext))), emptySet(), now).single()
        val onC = acceptForStore(relayable(listOf(redactForMesh(onB)), now), emptySet(), now).single()

        assertTrue(decodeSosPayload(onC.payload)!!.context!!.medical.isEmpty())
        assertEquals(REDACTED_AUTHOR, onC.authorName)
        assertEquals(2, onC.hopCount)
    }

    @Test
    fun `the responder's own name is not redacted`() {
        val sos = request()
        val ack = sosStateEvent(sos.id, responder, sos.lat, sos.lon, SosState.EN_ROUTE, now + 60_000)

        // The artboard says "Papunta na si Boy". Stripping the person volunteering to
        // walk into floodwater would remove the one name the requester needs.
        assertEquals("Boy R.", redactForMesh(ack).authorName)
    }

    @Test
    fun `a flood report is untouched by the SOS policy`() {
        val report = request().copy(type = "flood_report", payload = null, authorName = "Maria S.")

        assertEquals("Maria S.", redactForMesh(report).authorName)
    }

    // ------------------------------------------------------------ acknowledgement fold

    @Test
    fun `an ack relayed back moves the requester's state and names the responder`() {
        val sos = request(fullContext)
        val beaconing = sosStateEvent(sos.id, requester, sos.lat, sos.lon, SosState.BEACONING, now + 1_000)
        val ack = sosStateEvent(sos.id, responder, sos.lat, sos.lon, SosState.ACKNOWLEDGED, now + 40_000)

        val snapshot = SosReducer.snapshot(sos.id, listOf(sos, beaconing, ack), requester.authorId)!!

        assertEquals(SosState.ACKNOWLEDGED, snapshot.state)
        assertEquals("Boy R.", snapshot.claimedByName)
        assertEquals(
            "Nakita na ng barangay responder ang hiling mo.",
            requesterText(snapshot.state).first,
        )
    }

    @Test
    fun `an ack that arrives out of order still lands`() {
        val sos = request()
        val beaconing = sosStateEvent(sos.id, requester, sos.lat, sos.lon, SosState.BEACONING, now + 1_000)
        val unreachable = sosStateEvent(sos.id, requester, sos.lat, sos.lon, SosState.UNREACHABLE, now + 30_000)
        val enRoute = sosStateEvent(sos.id, responder, sos.lat, sos.lon, SosState.EN_ROUTE, now + 50_000)

        // The mesh promises no ordering, so the ack may be folded before the local
        // transitions it logically follows.
        val jumbled = SosReducer.snapshot(sos.id, listOf(enRoute, sos, unreachable, beaconing), requester.authorId)!!
        val ordered = SosReducer.snapshot(sos.id, listOf(sos, beaconing, unreachable, enRoute), requester.authorId)!!

        assertEquals(SosState.EN_ROUTE, jumbled.state)
        assertEquals(ordered.state, jumbled.state)
        assertEquals("Boy R.", jumbled.claimedByName)
    }

    @Test
    fun `a stale BEACONING relayed in later cannot un-acknowledge a request`() {
        val sos = request()
        val ack = sosStateEvent(sos.id, responder, sos.lat, sos.lon, SosState.ACKNOWLEDGED, now + 40_000)
        val staleBeacon = sosStateEvent(sos.id, requester, sos.lat, sos.lon, SosState.BEACONING, now + 90_000)

        val snapshot = SosReducer.snapshot(sos.id, listOf(sos, ack, staleBeacon), requester.authorId)!!

        assertEquals(SosState.ACKNOWLEDGED, snapshot.state)
    }

    @Test
    fun `marking safe closes a request a responder had already claimed`() {
        val sos = request()
        val enRoute = sosStateEvent(sos.id, responder, sos.lat, sos.lon, SosState.EN_ROUTE, now + 50_000)
        val safe = sosStateEvent(sos.id, requester, sos.lat, sos.lon, SosState.SAFE_SELF_RESOLVED, now + 80_000)

        val snapshot = SosReducer.snapshot(sos.id, listOf(sos, enRoute, safe), requester.authorId)!!

        assertEquals(SosState.SAFE_SELF_RESOLVED, snapshot.state)
        assertTrue(!snapshot.isActive)
        // Boy is still recorded as having answered. Closing a request does not unmake
        // the fact that somebody came — this is an append-only log, and the queue
        // already filters on isActive, so nothing shows a stale "on the way".
        assertEquals("Boy R.", snapshot.claimedByName)
    }

    // ------------------------------------------------------------------- queue routing

    @Test
    fun `the queue holds other people's open requests and not my own`() {
        val mine = request()
        val theirsEvent = newSosEvent(responder, 18.1720, 120.5940, 8f, now + 5_000)
        val closed = newSosEvent(responder, 18.1730, 120.5950, 8f, now + 6_000)
        val closedSafe = sosStateEvent(
            decodeSosPayload(closed.payload)!!.sosId,
            responder, closed.lat, closed.lon, SosState.SAFE_SELF_RESOLVED, now + 7_000,
        )
        val all = listOf(mine, theirsEvent, closed, closedSafe)

        val queue = SosReducer.activeOthers(all, requester.authorId)

        assertEquals(listOf(theirsEvent.id), queue.map { it.sosId })
        assertEquals(mine.id, SosReducer.activeMine(all, requester.authorId)?.sosId)
    }

    @Test
    fun `a relayed request reports the mesh as its origin so the queue can say so`() {
        val relayed = acceptForStore(listOf(redactForMesh(request())), emptySet(), now).single()

        val snapshot = SosReducer.snapshot(
            decodeSosPayload(relayed.payload)!!.sosId,
            listOf(relayed),
            "someone-else",
        )!!

        assertTrue(snapshot.arrivedByMesh)
        assertEquals(1, snapshot.hopCount)
        assertTrue(!snapshot.isMine)
    }
}
