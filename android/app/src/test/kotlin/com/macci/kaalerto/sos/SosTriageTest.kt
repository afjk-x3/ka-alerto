package com.macci.kaalerto.sos

import com.macci.kaalerto.data.Event
import com.macci.kaalerto.identity.LocalIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `QueueOfficial.dc.html`'s two feasible features.
 *
 * The tests that matter most here are the negative ones. Grouping must never lose a
 * request, and a false-alarm mark must never remove one — both are the only mechanisms
 * in this app capable of making a live emergency harder to see, so the assertions below
 * pin the boundary rather than the happy path.
 */
class SosTriageTest {

    private val now = 1_700_000_000_000L

    // Roughly 90 m apart at this latitude — comfortably outside SAME_INCIDENT_RADIUS_M.
    private val houseA = 18.1709 to 120.6058
    private val houseB = 18.1717 to 120.6058

    private fun snapshot(
        id: String,
        authorId: String,
        at: Pair<Double, Double> = houseA,
        minutesAgo: Long = 5,
        people: String? = "2–4",
        state: SosState = SosState.BEACONING,
    ) = SosSnapshot(
        sosId = id,
        startedAtMs = now - minutesAgo * 60_000,
        lat = at.first,
        lon = at.second,
        accuracyMeters = 8f,
        state = state,
        context = SosContext(people = people),
        authorName = "Residente $authorId",
        authorId = authorId,
        isMine = false,
        arrivedByMesh = true,
        hopCount = 1,
        claimedByName = null,
    )

    private fun mark(
        id: String,
        sosId: String,
        subjectAuthorId: String,
        minutesAgo: Long,
        undo: Boolean = false,
        role: String = LocalIdentity.ROLE_OFFICIAL,
        byName: String = "Kagawad 9AC6",
    ) = Event(
        id = id,
        type = if (undo) TYPE_SOS_FALSE_ALARM_UNDO else TYPE_SOS_FALSE_ALARM,
        lat = 0.0,
        lon = 0.0,
        featureRef = null,
        severity = null,
        waterLevel = null,
        authorId = "official-1",
        authorName = byName,
        authorRole = role,
        timestampMs = now - minutesAgo * 60_000,
        expiresAt = now + SOS_TTL_MS,
        origin = "local",
        hopCount = 0,
        note = null,
        payload = FalseAlarmPayload(sosId = sosId, subjectAuthorId = subjectAuthorId).encode(),
    )

    // ---- grouping ----

    @Test
    fun `requests at the same spot become one incident`() {
        val q = officialQueue(
            listOf(snapshot("s1", "dev-a"), snapshot("s2", "dev-b"), snapshot("s3", "dev-c")),
            TriageState(),
        )
        assertEquals(1, q.size)
        assertEquals(3, q.single().size)
    }

    @Test
    fun `grouping never loses a request`() {
        val all = listOf(
            snapshot("s1", "dev-a"),
            snapshot("s2", "dev-b"),
            snapshot("s3", "dev-c", at = houseB),
        )
        val q = officialQueue(all, TriageState())
        assertEquals(all.map { it.sosId }.toSet(), q.flatMap { it.all }.map { it.sosId }.toSet())
    }

    @Test
    fun `separate households stay separate`() {
        val q = officialQueue(
            listOf(snapshot("s1", "dev-a", at = houseA), snapshot("s2", "dev-b", at = houseB)),
            TriageState(),
        )
        assertEquals(2, q.size)
    }

    @Test
    fun `people buckets are listed, never summed`() {
        val q = officialQueue(
            listOf(snapshot("s1", "dev-a", people = "2–4"), snapshot("s2", "dev-b", people = "5–8")),
            TriageState(),
        )
        assertEquals(listOf("2–4", "5–8"), q.single().peopleBuckets)
    }

    @Test
    fun `a closed request is not queued at all`() {
        val q = officialQueue(
            listOf(snapshot("s1", "dev-a", state = SosState.SAFE_SELF_RESOLVED)),
            TriageState(),
        )
        assertTrue(q.isEmpty())
    }

    // ---- false alarm ----

    @Test
    fun `an official mark records who made it`() {
        val triage = foldTriage(listOf(mark("m1", "s1", "dev-a", 5)))
        assertEquals("Kagawad 9AC6", triage.marks["s1"]?.byName)
        assertEquals(1, triage.priorsByAuthor["dev-a"])
    }

    @Test
    fun `a mark from someone who is not an official is ignored`() {
        val triage = foldTriage(
            listOf(mark("m1", "s1", "dev-a", 5, role = LocalIdentity.ROLE_RESPONDER)),
        )
        assertNull(triage.marks["s1"])
        assertTrue(triage.priorsByAuthor.isEmpty())
    }

    @Test
    fun `any official can lift another official's mark`() {
        val triage = foldTriage(
            listOf(
                mark("m1", "s1", "dev-a", 30),
                mark("m2", "s1", "dev-a", 5, undo = true, byName = "Kagawad 71C4"),
            ),
        )
        assertNull(triage.marks["s1"])
        assertTrue(triage.priorsByAuthor.isEmpty())
    }

    @Test
    fun `mark and undo settle the same way whichever order the mesh delivers them`() {
        val events = listOf(
            mark("m1", "s1", "dev-a", 30),
            mark("m2", "s1", "dev-a", 5, undo = true),
        )
        assertEquals(foldTriage(events), foldTriage(events.reversed()))
    }

    @Test
    fun `a marked request sinks but is still in the queue`() {
        val requests = listOf(
            snapshot("s1", "dev-a", at = houseA, minutesAgo = 1),
            snapshot("s2", "dev-b", at = houseB, minutesAgo = 30),
        )
        val q = officialQueue(requests, foldTriage(listOf(mark("m1", "s1", "dev-a", 0))))
        assertEquals(2, q.size)
        assertEquals("s2", q.first().primary.sosId)
        assertEquals("s1", q.last().primary.sosId)
        assertNotNull(q.last().falseAlarm)
    }

    @Test
    fun `a device with prior marks is demoted on its next request, not hidden`() {
        // dev-a was marked on an older request; s3 is a brand new one from that device.
        val requests = listOf(
            snapshot("s3", "dev-a", at = houseA, minutesAgo = 1),
            snapshot("s2", "dev-b", at = houseB, minutesAgo = 30),
        )
        val q = officialQueue(requests, foldTriage(listOf(mark("m1", "s1", "dev-a", 200))))
        assertEquals(2, q.size)
        assertEquals("s2", q.first().primary.sosId)
        assertEquals("s3", q.last().primary.sosId)
        // Demoted, but visible and carrying no mark of its own — the new request has not
        // been judged, only the device's history is showing.
        assertNull(q.last().falseAlarm)
        assertEquals(1, q.last().priorFalseAlarms)
    }

    @Test
    fun `with nothing marked the queue is still newest first`() {
        val q = officialQueue(
            listOf(
                snapshot("old", "dev-a", at = houseA, minutesAgo = 40),
                snapshot("new", "dev-b", at = houseB, minutesAgo = 2),
            ),
            TriageState(),
        )
        assertEquals(listOf("new", "old"), q.map { it.primary.sosId })
    }

    @Test
    fun `triage events never carry a featureRef and so never become map markers`() {
        val identity = LocalIdentity.Identity("official-1", "Kagawad 9AC6", LocalIdentity.ROLE_OFFICIAL)
        val event = falseAlarmEvent(identity, snapshot("s1", "dev-a"), "dev-a", now, undo = false)
        assertNull(event.featureRef)
        assertNull(event.severity)
    }
}
