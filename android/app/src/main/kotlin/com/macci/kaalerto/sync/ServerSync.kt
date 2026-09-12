package com.macci.kaalerto.sync

import com.macci.kaalerto.data.Event
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Only these event types leave the device via the server transport. `sos*`,
 * `family_checkin`, `circle_invite`, and `role_*` events stay mesh-only — the server has
 * no auth (ground rule 4) and anyone who can reach it can read back everything ever
 * posted for a bbox with `GET /events?bbox=...`, no access control. See
 * `specs/2026-09-12-server-sync-design.md`'s Non-goals for the full reasoning.
 */
val SYNCED_TYPES = setOf("flood_report", "confirm", "dispute", "official_status")

/**
 * Every locally-held event worth pushing this cycle — mesh-received ones included, not
 * just self-authored, which is what makes carry-forward work: a device that relayed
 * someone else's report five hours ago, with no connectivity at the time, must still
 * upload it the next time it reaches a server.
 *
 * There is deliberately no push-side cursor anywhere in this file. A timestamp-based
 * "only push things newer than X" tracker would silently break exactly the carry-forward
 * case above — a relayed event's own `timestampMs` is old even though this device only
 * just received it. The server's `INSERT OR IGNORE` on the event's primary key already
 * makes re-posting something it has cost one indexed lookup and a `'duplicate'` status;
 * at this app's event volumes over a local WiFi link, that costs nothing that matters.
 */
fun eventsToSync(all: List<Event>): List<Event> = all.filter { it.type in SYNCED_TYPES }

/**
 * Turns what someone actually types into Profile's server-address field
 * ("192.168.1.42:3000", no scheme) into something [java.net.URL] accepts, and strips a
 * trailing slash so [buildBatchUrl]/[buildPullUrl] never produce a doubled "//".
 */
fun normalizeBaseUrl(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "http://$trimmed"
    }
}

fun buildBatchUrl(baseUrl: String): String = "${normalizeBaseUrl(baseUrl)}/events/batch"

/**
 * [since] is the server-assigned cursor from the last successful pull page — a
 * strictly-increasing `seq` (`server/src/db.js`), never a client timestamp. `server/src/db.js`'s
 * own doc comment: timestamps are "client-supplied, cannot be trusted for ordering, and
 * can collide across events."
 */
fun buildPullUrl(baseUrl: String, minLon: Double, minLat: Double, maxLon: Double, maxLat: Double, since: Long): String =
    "${normalizeBaseUrl(baseUrl)}/events?bbox=$minLon,$minLat,$maxLon,$maxLat&since=$since"

private val syncJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class BatchRequestWire(val events: List<Event>)

/**
 * The exact body for `POST /events/batch`. `Event` is already `@Serializable` — it *is*
 * the wire format, same reasoning as the mesh transport in `mesh/MeshProtocol.kt` — so
 * this is a direct encode with no field-by-field mapping to keep in sync by hand.
 */
fun encodeBatchRequest(events: List<Event>): String =
    syncJson.encodeToString(BatchRequestWire.serializer(), BatchRequestWire(events))

/** One page of `GET /events?bbox=...&since=...`'s response. */
data class PullPage(val events: List<Event>, val nextCursor: Long, val hasMore: Boolean)

@Serializable
private data class PullResponseWire(val events: List<Event>, val nextCursor: Long, val hasMore: Boolean)

/**
 * Returns null for anything that isn't the expected shape, matching this codebase's
 * `decodeCircleInvitePayload`/`decodeCircleCard` convention of failing closed rather than
 * throwing into a caller that isn't expecting it.
 */
fun decodePullResponse(json: String): PullPage? =
    runCatching { syncJson.decodeFromString(PullResponseWire.serializer(), json) }
        .getOrNull()
        ?.let { PullPage(events = it.events, nextCursor = it.nextCursor, hasMore = it.hasMore) }

/**
 * Stamps how *this device* learned about a pulled event — parallel to
 * `mesh/MeshProtocol.kt`'s `origin = "mesh"` relay stamp. `hopCount` is left untouched:
 * it is a mesh-specific measure, and a server pull is not a hop in that sense.
 */
fun stampServerOrigin(events: List<Event>): List<Event> = events.map { it.copy(origin = "server") }

fun buildHealthUrl(baseUrl: String): String = "${normalizeBaseUrl(baseUrl)}/health"

// `cursor` has no default: a malformed/unexpected response (e.g. missing the field
// entirely) must fail to decode rather than silently reading as cursor 0, which would be
// indistinguishable from a real fresh server and could wrongly suppress a legitimate
// cursor reset. `ignoreUnknownKeys` (set on `syncJson`) already tolerates `ok` and any
// other field this server's `/health` response happens to carry.
@Serializable
private data class HealthResponseWire(val cursor: Long)

/**
 * Returns null for anything that isn't the expected `/health` shape — same fail-closed
 * convention as [decodePullResponse]. A null result means "couldn't read the server's
 * cursor this cycle," not "the local cursor is stale" — [cursorIsStale] is only ever
 * consulted when this returns a real value.
 */
fun decodeHealthCursor(json: String): Long? =
    runCatching { syncJson.decodeFromString(HealthResponseWire.serializer(), json) }
        .getOrNull()
        ?.cursor

/**
 * True when the locally-stored pull cursor could not have come from the server currently
 * answering. `server/src/db.js`'s own doc comment: `seq` (the cursor) is "a server-assigned,
 * strictly monotonic counter" — so a local cursor higher than the server's own current
 * maximum is impossible unless the data underneath changed out from under it (the server's
 * database was reset or recreated between sessions, or the address now points at a
 * different server entirely). Left uncaught, `pullDelta` would request `since=<a cursor the
 * server never issued>` forever, get zero results back with no error, and
 * `SyncPrefs.setLastSyncedAtMs` would still stamp as if the pull succeeded — a permanently
 * dead pull path that reads as healthy. Treat it as staleness and reset to 0 (equivalent to
 * "sync everything again") rather than trusting a cursor the server can't have issued.
 */
fun cursorIsStale(localCursor: Long, serverCursor: Long): Boolean = localCursor > serverCursor

/**
 * The server (`server/src/server.js`) rejects any `POST /events/batch` body over
 * `MAX_BATCH_SIZE` (500 events) with an HTTP 413 that a retry of the same oversized batch
 * would fail again unchanged — a permanent silent push failure for a device whose
 * qualifying local event count ever crosses that line. Chunking safely under the server's
 * cap (400, leaving headroom) means every event still gets through, as a few requests
 * instead of one.
 */
const val MAX_PUSH_CHUNK_SIZE = 400

fun chunkForPush(events: List<Event>): List<List<Event>> = events.chunked(MAX_PUSH_CHUNK_SIZE)

/**
 * The delay before the next sync cycle, given how many cycles in a row produced no
 * successful push or pull. A device whose server has become unreachable (the normal
 * steady state once someone leaves the demo LAN, or the laptop running the server is
 * closed) backs off to [backedOffIntervalMs] after [failureThreshold] consecutive failed
 * cycles rather than retrying at [normalIntervalMs] forever — a real battery concern on
 * the app that built Storm Mode for the same reason. Two tiers, not a full exponential
 * curve: enough to close the gap without the fuller retry/circuit-breaker machinery this
 * feature's design already deliberately avoids.
 */
fun nextSyncDelayMs(
    consecutiveFailures: Int,
    normalIntervalMs: Long,
    backedOffIntervalMs: Long,
    failureThreshold: Int,
): Long = if (consecutiveFailures >= failureThreshold) backedOffIntervalMs else normalIntervalMs
