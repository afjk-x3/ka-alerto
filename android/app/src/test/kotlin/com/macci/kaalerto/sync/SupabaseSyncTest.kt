package com.macci.kaalerto.sync

import com.macci.kaalerto.data.Event
import com.macci.kaalerto.sos.REDACTED_AUTHOR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseSyncTest {

    private fun event(id: String, origin: String = "local", type: String = "flood_report") = Event(
        id = id,
        type = type,
        lat = 18.17,
        lon = 120.60,
        featureRef = "geohash-1",
        severity = "S1",
        waterLevel = "bukong-bukong",
        authorId = "local-a1",
        authorName = "A",
        authorRole = "resident",
        timestampMs = 1_700_000_000_000L,
        expiresAt = 1_700_000_100_000L,
        origin = origin,
        hopCount = 0,
        note = "Baha sa kalye",
    )

    @Test
    fun `the events url is the rest table, no extra path`() {
        assertEquals("https://x.supabase.co/rest/v1/events", buildEventsUrl("https://x.supabase.co"))
    }

    @Test
    fun `the pull url has no location filter and no cursor`() {
        val url = buildPullUrl("https://x.supabase.co")
        assertEquals("https://x.supabase.co/rest/v1/events?select=*", url)
    }

    @Test
    fun `an event round-trips through encode and decode unchanged`() {
        val e = event("e1")
        assertEquals(listOf(e), decodeEvents(encodeEvents(listOf(e))))
    }

    @Test
    fun `decodeEvents fails closed on garbage`() {
        assertNull(decodeEvents("not json"))
    }

    @Test
    fun `stampSupabaseOrigin marks every event as server-origin`() {
        val stamped = stampSupabaseOrigin(listOf(event("e1", origin = "mesh")))
        assertEquals("server", stamped.single().origin)
    }

    @Test
    fun `flood-reporting and SOS types are synced, family and role events are excluded`() {
        val events = listOf("flood_report", "confirm", "dispute", "official_status", "sos", "sos_amend", "sos_state",
            "family_checkin", "circle_invite", "role_grant").mapIndexed { i, t -> event("e$i", type = t) }

        assertEquals(setOf("e0", "e1", "e2", "e3", "e4", "e5", "e6"), eventsToSync(events).map { it.id }.toSet())
    }

    @Test
    fun `a centre status, an added shelter and a report withdrawal are synced`() {
        val events = listOf("evac_status", "flood_withdraw", "evac_centre").mapIndexed { i, t -> event("e$i", type = t) }

        assertEquals(setOf("e0", "e1", "e2"), eventsToSync(events).map { it.id }.toSet())
    }

    @Test
    fun `an SOS is redacted before it can reach Supabase`() {
        assertEquals(REDACTED_AUTHOR, eventsToSync(listOf(event("e1", type = "sos"))).single().authorName)
    }

    @Test
    fun `a mesh-received event is synced the same as a self-authored one`() {
        assertEquals(listOf("e1"), eventsToSync(listOf(event("e1", origin = "mesh"))).map { it.id })
    }

    @Test
    fun `sample reports never leave the phone`() {
        val events = listOf(event("s1", origin = "seed"), event("e1"), event("e2", origin = "mesh", type = "confirm"))

        assertEquals(setOf("e1", "e2"), eventsToSync(events).map { it.id }.toSet())
    }

    @Test
    fun `sync slows down after three failed cycles in a row, and speeds back up`() {
        assertEquals(30_000L, nextSyncDelayMs(0))
        assertEquals(30_000L, nextSyncDelayMs(SLOW_THRESHOLD - 1))
        assertEquals(120_000L, nextSyncDelayMs(SLOW_THRESHOLD))
        assertEquals(120_000L, nextSyncDelayMs(50))
    }

    // event() expires at 1_700_000_100_000.
    private val expiry = 1_700_000_100_000L

    @Test
    fun `a report is held back from the purge until a full push has succeeded since it expired`() {
        assertTrue(isAwaitingUpload(event("e1", origin = "local"), lastFullPushOkMs = 0L))
        assertTrue(isAwaitingUpload(event("e1", origin = "local"), lastFullPushOkMs = expiry - 1))
        assertFalse(isAwaitingUpload(event("e1", origin = "local"), lastFullPushOkMs = expiry))
        assertFalse(isAwaitingUpload(event("e1", origin = "local"), lastFullPushOkMs = expiry + 1))
    }

    @Test
    fun `a relayed report is held back too, because carry-forward has to upload it`() {
        assertTrue(isAwaitingUpload(event("e1", origin = "mesh"), lastFullPushOkMs = 0L))
    }

    @Test
    fun `things that never upload, or already came from the cloud, are never held back`() {
        assertFalse(isAwaitingUpload(event("s1", origin = "seed"), lastFullPushOkMs = 0L))
        assertFalse(isAwaitingUpload(event("c1", origin = "server"), lastFullPushOkMs = 0L))
        assertFalse(isAwaitingUpload(event("f1", type = "family_checkin"), lastFullPushOkMs = 0L))
    }
}
