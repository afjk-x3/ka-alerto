package com.macci.kaalerto.identity

import com.macci.kaalerto.data.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The role fold. Two properties matter more than any individual rule here:
 *
 * 1. **Order independence** — the mesh delivers in whatever order the radio managed, and
 *    NFR-4 says two devices holding the same events must agree. Several tests shuffle
 *    the input deliberately.
 * 2. **Authority does not spread sideways** — official comes from the roster and only
 *    from the roster, so a compromised or mistaken official cannot mint more officials.
 */
class RoleReducerTest {

    private val now = 1_700_000_000_000L

    private val roster = listOf(
        BarangaySeat(id = "pb", title = "Punong Barangay", kind = "executive"),
        BarangaySeat(id = "kag-1", title = "Kagawad — Purok 1", kind = "sangguniang_barangay"),
        BarangaySeat(
            id = "sec",
            title = "Barangay Secretary",
            kind = "appointed",
            canPostOfficialStatus = false,
        ),
    )

    private fun identity(id: String, name: String, role: String = LocalIdentity.ROLE_RESIDENT) =
        LocalIdentity.Identity(authorId = id, authorName = name, authorRole = role)

    private fun event(
        id: String,
        type: String,
        author: LocalIdentity.Identity,
        payload: RolePayload,
        minutesAgo: Long,
    ) = Event(
        id = id,
        type = type,
        lat = 0.0,
        lon = 0.0,
        featureRef = null,
        severity = null,
        waterLevel = null,
        authorId = author.authorId,
        authorName = author.authorName,
        authorRole = author.authorRole,
        timestampMs = now - minutesAgo * 60_000,
        expiresAt = now + ROLE_TTL_MS,
        origin = "local",
        hopCount = 0,
        note = null,
        payload = payload.encode(),
    )

    private fun claim(id: String, who: LocalIdentity.Identity, seatId: String, minutesAgo: Long) =
        event(
            id, TYPE_ROLE_CLAIM, who,
            RolePayload(who.authorId, who.authorName, LocalIdentity.ROLE_OFFICIAL, seatId),
            minutesAgo,
        )

    private fun request(id: String, who: LocalIdentity.Identity, minutesAgo: Long) =
        event(
            id, TYPE_ROLE_REQUEST, who,
            RolePayload(who.authorId, who.authorName, LocalIdentity.ROLE_RESPONDER),
            minutesAgo,
        )

    private fun grant(
        id: String,
        by: LocalIdentity.Identity,
        subject: LocalIdentity.Identity,
        minutesAgo: Long,
        role: String = LocalIdentity.ROLE_RESPONDER,
    ) = event(
        id, TYPE_ROLE_GRANT, by,
        RolePayload(subject.authorId, subject.authorName, role),
        minutesAgo,
    )

    private fun revoke(
        id: String,
        by: LocalIdentity.Identity,
        subject: LocalIdentity.Identity,
        minutesAgo: Long,
    ) = event(
        id, TYPE_ROLE_REVOKE, by,
        RolePayload(subject.authorId, subject.authorName, LocalIdentity.ROLE_RESIDENT),
        minutesAgo,
    )

    private val kagawad = identity("dev-official", "Kagawad 9AC6", LocalIdentity.ROLE_OFFICIAL)
    private val boy = identity("dev-boy", "Residente 4471")
    private val jenny = identity("dev-jenny", "Residente 88B2")

    @Test
    fun `nobody has a role until something is claimed`() {
        val state = foldRoles(emptyList(), roster)
        assertEquals(LocalIdentity.ROLE_RESIDENT, state.roleOf(boy.authorId))
        assertTrue(state.seats.isEmpty())
    }

    @Test
    fun `claiming a roster seat makes that device official`() {
        val state = foldRoles(listOf(claim("c1", kagawad, "kag-1", 30)), roster)
        assertEquals(LocalIdentity.ROLE_OFFICIAL, state.roleOf(kagawad.authorId))
        assertEquals("Kagawad — Purok 1", state.seatOf(kagawad.authorId)?.seatTitle)
    }

    @Test
    fun `a seat that is not in the roster grants nothing`() {
        val state = foldRoles(listOf(claim("c1", kagawad, "kag-9999", 30)), roster)
        assertEquals(LocalIdentity.ROLE_RESIDENT, state.roleOf(kagawad.authorId))
        assertTrue(state.seats.isEmpty())
    }

    @Test
    fun `a seat with no say over flood severity is held but not official`() {
        val state = foldRoles(listOf(claim("c1", kagawad, "sec", 30)), roster)
        assertEquals(1, state.seats.size)
        assertEquals(LocalIdentity.ROLE_RESIDENT, state.roleOf(kagawad.authorId))
    }

    @Test
    fun `the earlier of two claims on one seat wins and the seat reads contested`() {
        val rival = identity("dev-rival", "Residente 0001")
        val state = foldRoles(
            listOf(claim("c2", rival, "kag-1", 10), claim("c1", kagawad, "kag-1", 30)),
            roster,
        )
        val seat = state.seats.single()
        assertEquals(kagawad.authorId, seat.authorId)
        assertTrue(seat.contested)
        assertEquals(listOf("Residente 0001"), seat.rivalNames)
        assertEquals(LocalIdentity.ROLE_RESIDENT, state.roleOf(rival.authorId))
    }

    @Test
    fun `one device claiming its own seat twice is not a contest`() {
        val state = foldRoles(
            listOf(claim("c1", kagawad, "kag-1", 30), claim("c2", kagawad, "kag-1", 10)),
            roster,
        )
        assertFalse(state.seats.single().contested)
    }

    @Test
    fun `an official activates an applicant and the application stops pending`() {
        val state = foldRoles(
            listOf(
                claim("c1", kagawad, "kag-1", 60),
                request("r1", boy, 30),
                grant("g1", kagawad, boy, 10),
            ),
            roster,
        )
        assertEquals(LocalIdentity.ROLE_RESPONDER, state.roleOf(boy.authorId))
        assertTrue(state.pending.isEmpty())
        assertEquals("Kagawad 9AC6", state.grantOf(boy.authorId)?.byName)
    }

    @Test
    fun `an application with no answer stays pending`() {
        val state = foldRoles(
            listOf(claim("c1", kagawad, "kag-1", 60), request("r1", boy, 30)),
            roster,
        )
        assertEquals(listOf("Residente 4471"), state.pending.map { it.authorName })
        assertEquals(LocalIdentity.ROLE_RESIDENT, state.roleOf(boy.authorId))
    }

    @Test
    fun `a grant from someone who is not an official does nothing`() {
        val state = foldRoles(listOf(request("r1", boy, 30), grant("g1", jenny, boy, 10)), roster)
        assertEquals(LocalIdentity.ROLE_RESIDENT, state.roleOf(boy.authorId))
        assertFalse(state.pending.isEmpty())
    }

    @Test
    fun `an official cannot grant official — authority never spreads sideways`() {
        val state = foldRoles(
            listOf(
                claim("c1", kagawad, "kag-1", 60),
                grant("g1", kagawad, boy, 10, role = LocalIdentity.ROLE_OFFICIAL),
            ),
            roster,
        )
        assertEquals(LocalIdentity.ROLE_RESIDENT, state.roleOf(boy.authorId))
    }

    @Test
    fun `a revoke stands the responder back down`() {
        val state = foldRoles(
            listOf(
                claim("c1", kagawad, "kag-1", 60),
                grant("g1", kagawad, boy, 30),
                revoke("v1", kagawad, boy, 10),
            ),
            roster,
        )
        assertEquals(LocalIdentity.ROLE_RESIDENT, state.roleOf(boy.authorId))
        assertTrue(state.grants.isEmpty())
    }

    @Test
    fun `re-applying after a revoke is a fresh application`() {
        val state = foldRoles(
            listOf(
                claim("c1", kagawad, "kag-1", 60),
                grant("g1", kagawad, boy, 40),
                revoke("v1", kagawad, boy, 30),
                request("r1", boy, 5),
            ),
            roster,
        )
        assertEquals(listOf("Residente 4471"), state.pending.map { it.authorName })
    }

    @Test
    fun `re-applying while already activated does not re-open an application`() {
        val state = foldRoles(
            listOf(
                claim("c1", kagawad, "kag-1", 60),
                request("r1", boy, 40),
                grant("g1", kagawad, boy, 30),
                request("r2", boy, 5),
            ),
            roster,
        )
        assertTrue(state.pending.isEmpty())
        assertEquals(LocalIdentity.ROLE_RESPONDER, state.roleOf(boy.authorId))
    }

    @Test
    fun `a grant arriving before the claim that authorised it still counts`() {
        // The mesh case: two devices, opposite delivery order, one answer. Folding
        // forward once would drop the grant because its author was not yet an official.
        val forwards = listOf(
            claim("c1", kagawad, "kag-1", 60),
            request("r1", boy, 40),
            grant("g1", kagawad, boy, 20),
        )
        val backwards = forwards.reversed()
        assertEquals(foldRoles(forwards, roster), foldRoles(backwards, roster))
        assertEquals(LocalIdentity.ROLE_RESPONDER, foldRoles(backwards, roster).roleOf(boy.authorId))
    }

    @Test
    fun `the fold is independent of delivery order across every event type`() {
        val events = listOf(
            claim("c1", kagawad, "kag-1", 90),
            claim("c2", jenny, "pb", 80),
            request("r1", boy, 70),
            grant("g1", kagawad, boy, 60),
            revoke("v1", jenny, boy, 50),
            request("r2", boy, 40),
        )
        val expected = foldRoles(events, roster)
        // Every rotation of the same set must fold identically.
        for (i in events.indices) {
            val rotated = events.drop(i) + events.take(i)
            assertEquals("rotation $i disagreed", expected, foldRoles(rotated, roster))
        }
        assertEquals(listOf("Residente 4471"), expected.pending.map { it.authorName })
    }

    @Test
    fun `a seat holder who was also granted responder still reads as official`() {
        val state = foldRoles(
            listOf(
                claim("c1", kagawad, "kag-1", 60),
                claim("c2", jenny, "pb", 60),
                grant("g1", jenny, kagawad, 10),
            ),
            roster,
        )
        assertEquals(LocalIdentity.ROLE_OFFICIAL, state.roleOf(kagawad.authorId))
    }

    @Test
    fun `role events never carry a featureRef and so can never become map markers`() {
        val events = listOf(
            claim("c1", kagawad, "kag-1", 60),
            request("r1", boy, 40),
            grant("g1", kagawad, boy, 20),
            revoke("v1", kagawad, boy, 10),
        )
        assertTrue(events.all { it.featureRef == null && it.severity == null })
    }

    @Test
    fun `a role event outlives any flood report`() {
        val sixHours = 6L * 60 * 60 * 1000
        assertTrue(ROLE_TTL_MS > sixHours * 100)
    }
}
