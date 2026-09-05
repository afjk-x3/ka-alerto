package com.macci.kaalerto.mesh

import com.macci.kaalerto.data.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mesh's radio half needs two physical phones and cannot run here. Its *decisions*
 * — what gets relayed, what gets dropped, how a batch is split, whether two builds
 * agree on the wire format — are pure functions, and those are what this covers.
 */
class MeshProtocolTest {

    private val now = 1_700_000_000_000L

    private fun event(
        id: String,
        expiresAt: Long = now + 60_000L,
        hopCount: Int = 0,
        note: String? = null,
    ) = Event(
        id = id,
        type = "flood_report",
        lat = 18.17,
        lon = 120.59,
        featureRef = "wz0j1abc",
        severity = "S2",
        waterLevel = "knee",
        authorId = "author-1",
        authorName = "Residente A1B2",
        authorRole = "resident",
        timestampMs = now - 60_000L,
        expiresAt = expiresAt,
        origin = "local",
        hopCount = hopCount,
        note = note,
    )

    @Test
    fun `relayable drops expired events`() {
        val fresh = event("fresh")
        val expired = event("expired", expiresAt = now - 1)

        assertEquals(listOf(fresh), relayable(listOf(fresh, expired), now))
    }

    @Test
    fun `relayable drops events at the hop limit`() {
        val travelled = event("travelled", hopCount = MESH_MAX_HOPS - 1)
        val exhausted = event("exhausted", hopCount = MESH_MAX_HOPS)

        assertEquals(listOf(travelled), relayable(listOf(travelled, exhausted), now))
    }

    @Test
    fun `small batches stay in a single payload`() {
        val events = (1..19).map { event("seed-$it") }

        assertEquals(1, chunkForPayload(events).size)
    }

    @Test
    fun `batches split before exceeding Nearby's payload cap`() {
        // A note long enough that a handful of events blows past the limit — the point
        // is that size, not count, decides where the split lands.
        val fat = (1..40).map { event("fat-$it", note = "x".repeat(2_000)) }

        val batches = chunkForPayload(fat)

        assertTrue("expected more than one batch, got ${batches.size}", batches.size > 1)
        assertEquals(fat, batches.flatten())
        batches.forEach { batch ->
            val encoded = meshJson.encodeToString(MeshMessage.serializer(), MeshMessage.Events(batch))
            assertTrue("batch of ${batch.size} serialised to ${encoded.length} bytes", encoded.toByteArray().size < 32_000)
        }
    }

    @Test
    fun `a single oversized event still gets its own batch rather than being dropped`() {
        val huge = event("huge", note = "x".repeat(MESH_MAX_BATCH_BYTES * 2))

        assertEquals(listOf(listOf(huge)), chunkForPayload(listOf(huge)))
    }

    @Test
    fun `a received event is stored as mesh with its hop count incremented`() {
        val stored = acceptForStore(listOf(event("a")), knownIds = emptySet(), nowMs = now)

        assertEquals(1, stored.size)
        assertEquals("mesh", stored[0].origin)
        assertEquals(1, stored[0].hopCount)
        // The content hash must survive the rewrite, or the same report arriving later
        // over SMS or the server would insert a second row instead of deduping.
        assertEquals("a", stored[0].id)
    }

    @Test
    fun `an event already held is neither stored nor forwarded`() {
        val stored = acceptForStore(listOf(event("a")), knownIds = setOf("a"), nowMs = now)

        // This is what stops a flood looping forever in a cluster: a report coming back
        // around a cycle is already known, so nothing is stored and nothing is re-shared.
        assertTrue(stored.isEmpty())
    }

    @Test
    fun `an event relayed to the hop limit is stored but not forwarded again`() {
        val stored = acceptForStore(
            listOf(event("far", hopCount = MESH_MAX_HOPS - 1)),
            knownIds = emptySet(),
            nowMs = now,
        )

        // The last device on the chain still gets to see the report — it just becomes a
        // dead end, so hopCount cannot grow without bound in a pathological topology.
        assertEquals(listOf(MESH_MAX_HOPS), stored.map { it.hopCount })
        assertTrue(relayable(stored, now).isEmpty())
    }

    @Test
    fun `phone C receives phone A's report through B at two hops`() {
        // The days 6-7 DoD, as far as it can be checked without radios: A authors it, B
        // takes it in at one hop, B relays what was new to it, C takes it in at two.
        val authoredOnA = event("a-report")

        val onB = acceptForStore(listOf(authoredOnA), knownIds = emptySet(), nowMs = now)
        val relayedByB = relayable(onB, now)
        val onC = acceptForStore(relayedByB, knownIds = emptySet(), nowMs = now)

        assertEquals(listOf(1), onB.map { it.hopCount })
        assertEquals(listOf(2), onC.map { it.hopCount })
        assertEquals("mesh", onC.single().origin)
    }

    @Test
    fun `manifest and events round-trip through the wire format`() {
        val manifest: MeshMessage = MeshMessage.Manifest(listOf("a", "b"))
        val events: MeshMessage = MeshMessage.Events(listOf(event("e1"), event("e2", note = "hanggang tuhod")))

        for (message in listOf(manifest, events)) {
            val encoded = meshJson.encodeToString(MeshMessage.serializer(), message)
            assertEquals(message, meshJson.decodeFromString(MeshMessage.serializer(), encoded))
        }
    }

    @Test
    fun `a radio that is off names itself rather than reporting a working mesh`() {
        assertEquals(null, MeshRadios.Readiness(bluetoothOn = true, locationOn = true).message)
        assertTrue(MeshRadios.Readiness(bluetoothOn = false, locationOn = true).message!!.contains("Bluetooth"))
        assertTrue(MeshRadios.Readiness(bluetoothOn = true, locationOn = false).message!!.contains("Location"))

        // The airplane-mode case BUILD_TASKS.md days 6-7 says to verify on day 6: both
        // are off at once, and naming only one of them sends the resident back twice.
        val both = MeshRadios.Readiness(bluetoothOn = false, locationOn = false)
        assertTrue(both.message!!.contains("Bluetooth"))
        assertTrue(both.message!!.contains("Location"))
        assertTrue(listOf(both).none { it.ready })
    }

    @Test
    fun `the wire format carries the author name so a receiver renders it offline`() {
        val encoded = meshJson.encodeToString(MeshMessage.serializer(), MeshMessage.Events(listOf(event("e1"))))

        // CLAUDE.md's guardrail: the display name is embedded at creation, never looked
        // up. If it ever stops travelling with the event, a mesh-received report renders
        // anonymously on a phone with no way to resolve it.
        assertTrue(encoded.contains("Residente A1B2"))
    }
}
