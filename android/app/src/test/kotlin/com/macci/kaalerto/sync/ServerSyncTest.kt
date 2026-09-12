package com.macci.kaalerto.sync

import com.macci.kaalerto.data.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSyncTest {

    private fun event(id: String, type: String, origin: String = "local", hopCount: Int = 0) = Event(
        id = id,
        type = type,
        lat = 18.17,
        lon = 120.60,
        featureRef = if (type == "flood_report") "geohash-1" else null,
        severity = "S1",
        waterLevel = null,
        authorId = "local-a1",
        authorName = "A",
        authorRole = "resident",
        timestampMs = 1_700_000_000_000L,
        expiresAt = 1_700_000_100_000L,
        origin = origin,
        hopCount = hopCount,
        note = null,
    )

    @Test
    fun `flood-reporting types are synced, everything else is excluded`() {
        val events = listOf(
            event("e1", "flood_report"),
            event("e2", "confirm"),
            event("e3", "dispute"),
            event("e4", "official_status"),
            event("e5", "sos"),
            event("e6", "family_checkin"),
            event("e7", "circle_invite"),
            event("e8", "role_grant"),
        )

        val result = eventsToSync(events)

        assertEquals(setOf("e1", "e2", "e3", "e4"), result.map { it.id }.toSet())
    }

    @Test
    fun `a mesh-received event is synced the same as a self-authored one`() {
        val relayed = event("e1", "flood_report", origin = "mesh", hopCount = 3)

        assertEquals(listOf("e1"), eventsToSync(listOf(relayed)).map { it.id })
    }

    @Test
    fun `normalizeBaseUrl adds a scheme when the user typed a bare host`() {
        assertEquals("http://192.168.1.42:3000", normalizeBaseUrl("192.168.1.42:3000"))
    }

    @Test
    fun `normalizeBaseUrl leaves an explicit scheme alone`() {
        assertEquals("https://sync.example.com", normalizeBaseUrl("https://sync.example.com"))
    }

    @Test
    fun `normalizeBaseUrl trims a trailing slash so paths never double up`() {
        assertEquals("http://192.168.1.42:3000", normalizeBaseUrl("192.168.1.42:3000/"))
    }

    @Test
    fun `normalizeBaseUrl trims surrounding whitespace from a pasted address`() {
        assertEquals("http://192.168.1.42:3000", normalizeBaseUrl("  192.168.1.42:3000  "))
    }

    @Test
    fun `buildBatchUrl appends the batch path to the normalized base`() {
        assertEquals("http://192.168.1.42:3000/events/batch", buildBatchUrl("192.168.1.42:3000"))
    }

    @Test
    fun `buildPullUrl carries the bbox and cursor as query params`() {
        val url = buildPullUrl("192.168.1.42:3000", minLon = 120.599, minLat = 18.166, maxLon = 120.613, maxLat = 18.176, since = 42)

        assertEquals("http://192.168.1.42:3000/events?bbox=120.599,18.166,120.613,18.176&since=42", url)
    }

    @Test
    fun `encodeBatchRequest wraps the events under an events key, Event's own field names untouched`() {
        val body = encodeBatchRequest(listOf(event("e1", "flood_report")))

        assertTrue(body.startsWith("{\"events\":["))
        assertTrue(body.contains("\"id\":\"e1\""))
        assertTrue(body.contains("\"type\":\"flood_report\""))
    }

    @Test
    fun `decodePullResponse parses a well-formed page`() {
        val json = """{"events":[],"nextCursor":7,"hasMore":true}"""

        val page = decodePullResponse(json)

        assertEquals(0, page?.events?.size)
        assertEquals(7L, page?.nextCursor)
        assertTrue(page?.hasMore == true)
    }

    @Test
    fun `decodePullResponse decodes the events array using Event's own serializer`() {
        val json = """{"events":[{"id":"e9","type":"flood_report","lat":18.17,"lon":120.6,""" +
            """"featureRef":null,"severity":"S1","waterLevel":null,"authorId":"a","authorName":"A",""" +
            """"authorRole":"resident","timestampMs":1000,"expiresAt":2000,"origin":"local",""" +
            """"hopCount":0,"note":null}],"nextCursor":1,"hasMore":false}"""

        val page = decodePullResponse(json)

        assertEquals(1, page?.events?.size)
        assertEquals("e9", page?.events?.single()?.id)
    }

    @Test
    fun `decodePullResponse returns null for garbage rather than throwing`() {
        assertNull(decodePullResponse("not json"))
        assertNull(decodePullResponse("""{"wrong":"shape"}"""))
    }

    @Test
    fun `stampServerOrigin overwrites origin regardless of what the event carried`() {
        val mixed = listOf(
            event("e1", "flood_report", origin = "local"),
            event("e2", "flood_report", origin = "mesh", hopCount = 2),
        )

        val stamped = stampServerOrigin(mixed)

        assertTrue(stamped.all { it.origin == "server" })
        assertEquals(2, stamped.last().hopCount) // hopCount is untouched — not a mesh hop measure here
    }
}
