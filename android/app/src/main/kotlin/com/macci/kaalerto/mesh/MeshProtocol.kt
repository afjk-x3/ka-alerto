package com.macci.kaalerto.mesh

import com.macci.kaalerto.data.Event
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Nearby matches peers on this string alone — every KaAlerto install advertises and discovers it. */
const val MESH_SERVICE_ID = "com.macci.kaalerto.mesh"

/**
 * docs/03-architecture.md §415 puts the hop limit *in the event* (`TTL = (max_hops,
 * expiry_time)`). The day-2 schema has the expiry half (`Event.expiresAt`) but no
 * `max_hops` column, so a single build-wide constant stands in for it. Dedup alone
 * already terminates flooding — a device only re-shares what was genuinely new to it,
 * so a cycle dies on its second visit — this is the second, independent bound, so a
 * pathological topology can't grow `hopCount` without limit.
 */
const val MESH_MAX_HOPS = 5

/**
 * Nearby caps a `Payload.fromBytes` at 32 KB. Batches are built to stay comfortably
 * under that including the envelope's own JSON, rather than assuming the event count
 * stays small — 19 seed reports fit in one payload today, a barangay's worth would not.
 */
const val MESH_MAX_BATCH_BYTES = 24_000

/**
 * `ignoreUnknownKeys` so a peer running a newer build that added an `Event` column can
 * still talk to this one: the extra field is dropped rather than failing the whole
 * batch. Same reasoning as [com.macci.kaalerto.data.SeedLoader]'s parser.
 */
val meshJson = Json { ignoreUnknownKeys = true }

/**
 * The mesh wire format — plain JSON, per BUILD_TASKS.md days 6–7 ("send diff as JSON").
 * `docs/03-architecture.md` §407 specifies CBOR with signed payloads for the real
 * transport; this build is deliberately the simplified version (ground rule 4: no
 * crypto, no auth), so nothing here is signed and nothing verifies a sender.
 *
 * Two messages, exchanged in that order on every connection:
 *
 *  1. [Manifest] — "here is every event ID I hold". Cheap: IDs only, no payloads.
 *  2. [Events] — "here are the ones you were missing", possibly split across several
 *     payloads (see [MESH_MAX_BATCH_BYTES]).
 *
 * This is an anti-entropy exchange, not a broadcast: neither side ships an event the
 * other already has, so two devices that have already reconciled fall silent instead of
 * re-sending 19 reports every time they drift in and out of range.
 */
@Serializable
sealed interface MeshMessage {
    @Serializable
    @SerialName("manifest")
    data class Manifest(val ids: List<String>) : MeshMessage

    @Serializable
    @SerialName("events")
    data class Events(val events: List<Event>) : MeshMessage
}

/**
 * Splits [events] into batches that each serialise under [MESH_MAX_BATCH_BYTES].
 * Measured rather than counted — events vary a lot in size (a report with a long note
 * against a bare confirm), so a fixed "20 per batch" would be both wasteful and unsafe.
 */
fun chunkForPayload(events: List<Event>): List<List<Event>> {
    val batches = mutableListOf<List<Event>>()
    var current = mutableListOf<Event>()
    var currentBytes = 0

    for (event in events) {
        val size = meshJson.encodeToString(Event.serializer(), event).toByteArray().size + 1
        if (current.isNotEmpty() && currentBytes + size > MESH_MAX_BATCH_BYTES) {
            batches += current
            current = mutableListOf()
            currentBytes = 0
        }
        current += event
        currentBytes += size
    }
    if (current.isNotEmpty()) batches += current
    return batches
}

/**
 * What this device is willing to pass on: not expired, and not already at the hop
 * limit. Applied both when answering a peer's manifest and when re-sharing something
 * just received, so a stale or over-travelled event dies at the first device that sees
 * it rather than at every device downstream.
 */
fun relayable(events: List<Event>, nowMs: Long): List<Event> =
    events.filter { it.expiresAt > nowMs && it.hopCount < MESH_MAX_HOPS }

/**
 * The whole receive decision: given what just arrived and what this device already
 * holds, which events does it store, and how are they rewritten?
 *
 * Pulled out of [MeshService] deliberately. The radio half of the mesh needs two
 * physical phones and cannot be tested here, but *this* is where multi-hop is actually
 * decided — an event survives only if it is unexpired, under the hop limit, and new to
 * this device — and it is a pure function of three arguments, so it can be. The service
 * then forwards exactly this result (minus anything the hop increment just pushed to
 * the limit), which is what makes the flood terminate.
 *
 * `origin` and `hopCount` are rewritten, `id` is not: the id is the content hash, so
 * the same report still dedupes against copies arriving by server or SMS.
 */
fun acceptForStore(incoming: List<Event>, knownIds: Set<String>, nowMs: Long): List<Event> =
    relayable(incoming, nowMs)
        .filter { it.id !in knownIds }
        .map { it.copy(origin = "mesh", hopCount = it.hopCount + 1) }
