package com.macci.kaalerto.sync

import com.macci.kaalerto.data.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseSyncTest {

    private fun event(id: String, origin: String = "local") = Event(
        id = id,
        type = "flood_report",
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
}
