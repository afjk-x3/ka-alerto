# Server Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Android app a working connection to the already-built `server/` (Express + node:sqlite) so `flood_report`/`confirm`/`dispute`/`official_status` events reach any device that can reach the server — not just ones in mesh range — and pull back what other devices have posted for the demo area.

**Architecture:** A new `sync/` package: pure request/response logic (`ServerSync.kt`, fully unit-testable, no Context or network dependency), a small local-state store (`SyncPrefs.kt`, same shape as `geofence/HomeLocationStore.kt`), and a coroutine loop (`ServerSyncLoop.kt`, same shape as `family/CircleCheckInNotifier.kt`) that runs from `KaAlertoApplication` on a fixed interval, pushing everything locally held that matches the synced types (no push-side cursor — see Global Constraints) and pulling the bbox-scoped delta since the last cursor. `HttpURLConnection` does the actual I/O — no new dependency. One field in Profile lets a person type the server's LAN address; blank means sync does nothing. One line in `server/src/db.js` fixes a field-name mismatch that's been silently dropping data since the server was built.

**Tech Stack:** Kotlin, coroutines, `HttpURLConnection`, kotlinx.serialization (existing dependency, no new one), Node.js/Express/node:sqlite (existing server, unchanged except the one-line fix).

**Spec:** [specs/2026-09-12-server-sync-design.md](../specs/2026-09-12-server-sync-design.md)

## Global Constraints

- **Only `flood_report`, `confirm`, `dispute`, `official_status` events ever leave the device via this transport.** `sos*`, `family_checkin`, `circle_invite`, `role_*` stay mesh-only — the server has no auth and is readable by anyone who can reach it. Never widen `SYNCED_TYPES` without an explicit new decision.
- **No push-side cursor, ever.** Every sync cycle pushes every locally-held event matching `SYNCED_TYPES`, mesh-received ones included, not just self-authored. This is deliberate — see `sync/ServerSync.kt`'s doc comment — not a shortcut to fix later.
- **The pull cursor is the server's `seq`, never a client-supplied timestamp.** `server/src/db.js`'s own doc comment: timestamps "cannot be trusted for ordering, and can collide across events."
- **No new HTTP client dependency.** `HttpURLConnection` + the existing `kotlinx.serialization` dependency only. `Event` is already `@Serializable` — encode/decode it directly, never hand-build JSON strings.
- **A blank or unset server URL means every sync cycle is a silent no-op** — never an error, never a toast, never a blocked screen. Matches this app's "every transport is optional" rule (see the `uses-feature` block in `AndroidManifest.xml`).
- **`Event.lat`/`Event.lon` bbox values come from `demo.DemoArea.bounds`** — the only region this build ever needs. Never widen the pull to the whole planet.
- **Filipino is the base language everywhere user-facing**; every string goes through `com.macci.kaalerto.i18n.tr(fil, en)` (composable), matching every other screen in the app.
- **No crypto, no accounts, no signing** (ground rule 4) — the server trusts posted content outright, same as it already does today. This plan does not change that.

---

### Task 1: Pure sync request/response logic

**Files:**
- Create: `android/app/src/main/kotlin/com/macci/kaalerto/sync/ServerSync.kt`
- Test: `android/app/src/test/kotlin/com/macci/kaalerto/sync/ServerSyncTest.kt`

**Interfaces:**
- Consumes: `com.macci.kaalerto.data.Event` (existing, already `@Serializable`).
- Produces: `val SYNCED_TYPES: Set<String>`, `fun eventsToSync(all: List<Event>): List<Event>`, `fun normalizeBaseUrl(input: String): String`, `fun buildBatchUrl(baseUrl: String): String`, `fun buildPullUrl(baseUrl: String, minLon: Double, minLat: Double, maxLon: Double, maxLat: Double, since: Long): String`, `fun encodeBatchRequest(events: List<Event>): String`, `data class PullPage(val events: List<Event>, val nextCursor: Long, val hasMore: Boolean)`, `fun decodePullResponse(json: String): PullPage?`, `fun stampServerOrigin(events: List<Event>): List<Event>`.

This is the entire testable core of the feature — no `Context`, no real network, no Room. Task 4's `ServerSyncLoop` is a thin, untested-by-unit-test shell around these functions, exactly the same boundary this codebase already draws between `family/CircleStore.kt`'s pure `effectiveCircle` and its Context-touching `CircleStore` object.

- [ ] **Step 1: Write the failing tests**

Create `android/app/src/test/kotlin/com/macci/kaalerto/sync/ServerSyncTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.macci.kaalerto.sync.ServerSyncTest"`
Expected: FAIL — `ServerSync.kt` does not exist yet, compilation error ("unresolved reference: eventsToSync" etc.).

- [ ] **Step 3: Write the implementation**

Create `android/app/src/main/kotlin/com/macci/kaalerto/sync/ServerSync.kt`:

```kotlin
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.macci.kaalerto.sync.ServerSyncTest"`
Expected: PASS, all 13 tests green.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/macci/kaalerto/sync/ServerSync.kt android/app/src/test/kotlin/com/macci/kaalerto/sync/ServerSyncTest.kt
git commit -m "feat(android): pure server-sync request/response logic"
```

---

### Task 2: Local sync state store

**Files:**
- Create: `android/app/src/main/kotlin/com/macci/kaalerto/sync/SyncPrefs.kt`

**Interfaces:**
- Produces: `object SyncPrefs { fun getServerUrl(context: Context): String?; fun setServerUrl(context: Context, url: String?); fun getCursor(context: Context): Long; fun setCursor(context: Context, cursor: Long); fun getLastSyncedAtMs(context: Context): Long?; fun setLastSyncedAtMs(context: Context, atMs: Long) }`.

No unit test for this task — it is a thin `Context`/`SharedPreferences` wrapper, the same untested-by-unit-test precedent `geofence/HomeLocationStore.kt` and `family/CircleStore.kt`'s `CircleStore` object already set in this codebase (no test file exists for either). Correctness is proven by the manual device verification in Task 7.

- [ ] **Step 1: Write the implementation**

Create `android/app/src/main/kotlin/com/macci/kaalerto/sync/SyncPrefs.kt`:

```kotlin
package com.macci.kaalerto.sync

import android.content.Context

/**
 * Local-only sync state — same small-SharedPreferences-file shape as
 * `geofence/HomeLocationStore.kt`. The server address lives here rather than alongside
 * `identity/LocalIdentity.kt`'s own prefs, even though `identity/ProfileScreen.kt` is
 * what edits it: `HomeLocationStore` already sets this precedent, living next to its
 * actual consumer (`geofence/GeofenceNotifier.kt`) rather than the screen that writes to
 * it.
 */
object SyncPrefs {
    private const val PREFS = "kaalerto_sync"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_CURSOR = "cursor"
    private const val KEY_LAST_SYNCED_AT_MS = "last_synced_at_ms"

    /**
     * Null (the default — nothing has ever been saved, or an empty string was saved)
     * means sync is off. `sync/ServerSyncLoop.kt` skips its whole cycle body rather than
     * trying an empty URL.
     */
    fun getServerUrl(context: Context): String? = prefs(context).getString(KEY_SERVER_URL, null)?.ifBlank { null }

    fun setServerUrl(context: Context, url: String?) {
        prefs(context).edit().putString(KEY_SERVER_URL, url).apply()
    }

    /**
     * The pull cursor — a server-assigned `seq`, never a client timestamp. `0` is the
     * correct starting value: `server/src/db.js`'s `selectEventsSince` treats `since=0`
     * as "everything," same as a device that has never synced before.
     */
    fun getCursor(context: Context): Long = prefs(context).getLong(KEY_CURSOR, 0L)

    fun setCursor(context: Context, cursor: Long) {
        prefs(context).edit().putLong(KEY_CURSOR, cursor).apply()
    }

    /** Null means "no sync has ever succeeded" — rendered by `identity/ProfileFields.kt`'s
     * `ServerUrlField` as "no sync yet," never a bad-news state. */
    fun getLastSyncedAtMs(context: Context): Long? =
        prefs(context).getLong(KEY_LAST_SYNCED_AT_MS, -1L).takeIf { it >= 0 }

    fun setLastSyncedAtMs(context: Context, atMs: Long) {
        prefs(context).edit().putLong(KEY_LAST_SYNCED_AT_MS, atMs).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `cd android && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew compileDebugKotlin -q`
Expected: succeeds with no output.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/com/macci/kaalerto/sync/SyncPrefs.kt
git commit -m "feat(android): local sync state (server URL, pull cursor, last-synced time)"
```

---

### Task 3: Cleartext exception for the local-network server

**Files:**
- Create: `android/app/src/main/res/xml/network_security_config.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Interfaces:** none — this is a build/manifest-only task.

No unit test — matches the family-checkin plan's own Task 5 precedent ("a build-configuration change, verified by a successful compile/assemble").

**Why this has to be app-wide, not scoped to the server's address:** Android's network security config `<domain>` element only matches an exact or wildcard *hostname* — there is no schema construct for an IP or CIDR range. The server's actual address is whatever a person types into Profile at runtime; a manifest-compiled XML resource cannot be scoped to a value that does not exist yet at build time. See `specs/2026-09-12-server-sync-design.md`'s "Cleartext traffic exception" section for the full reasoning — this was a real correction made during that spec's review, not the original plan.

- [ ] **Step 1: Create the network security config**

Create `android/app/src/main/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  Permits plain-HTTP connections app-wide so `sync/ServerSyncLoop.kt` can reach a
  locally-hosted demo server with no HTTPS. See
  specs/2026-09-12-server-sync-design.md's "Cleartext traffic exception" section for why
  this cannot be scoped narrower: Android's network security config only matches by
  hostname, never by IP/CIDR range, and the server's actual address is whatever a person
  types into Profile at runtime — there is nothing static to scope a <domain-config> to.
  The only cleartext call this app ever makes is that one sync request; every other
  network call this app makes (tile loading, geocoding) already uses HTTPS regardless of
  what this policy permits.
-->
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

- [ ] **Step 2: Reference it from the manifest**

In `android/app/src/main/AndroidManifest.xml`, find the `<application ...>` opening tag:

```xml
    <application
        android:name=".KaAlertoApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_schemes"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.KaAlerto">
```

Add `android:networkSecurityConfig="@xml/network_security_config"` to it:

```xml
    <application
        android:name=".KaAlertoApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_schemes"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:networkSecurityConfig="@xml/network_security_config"
        android:supportsRtl="true"
        android:theme="@style/Theme.KaAlerto">
```

- [ ] **Step 3: Verify the build resolves and compiles**

Run: `cd android && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew assembleDebug -q`
Expected: succeeds with no output.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/res/xml/network_security_config.xml android/app/src/main/AndroidManifest.xml
git commit -m "build(android): permit cleartext HTTP for local-network server sync"
```

---

### Task 4: The sync loop, wired into the application

**Files:**
- Create: `android/app/src/main/kotlin/com/macci/kaalerto/sync/ServerSyncLoop.kt`
- Modify: `android/app/src/main/kotlin/com/macci/kaalerto/KaAlertoApplication.kt`

**Interfaces:**
- Consumes: Task 1's `eventsToSync`, `buildBatchUrl`, `buildPullUrl`, `encodeBatchRequest`, `decodePullResponse`, `stampServerOrigin`; Task 2's `SyncPrefs`; existing `com.macci.kaalerto.data.EventRepository`, `com.macci.kaalerto.data.KaAlertoDatabase`, `com.macci.kaalerto.demo.DemoArea`.
- Produces: `class ServerSyncLoop(context: Context) { fun start(scope: CoroutineScope) }`, `ServerSyncLoop.SYNC_INTERVAL_MS`.

No unit test for this task — it is `Context`- and network-touching orchestration, the same boundary `family/CircleCheckInNotifier.kt` and `geofence/GeofenceNotifier.kt` already draw around their own `start(scope)` methods (neither has a dedicated unit test; both are verified by manual device checks). All of this class's actual decision-making already has unit test coverage from Task 1 — this class only wires those pure functions to real I/O.

- [ ] **Step 1: Write the implementation**

Create `android/app/src/main/kotlin/com/macci/kaalerto/sync/ServerSyncLoop.kt`:

```kotlin
package com.macci.kaalerto.sync

import android.content.Context
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.demo.DemoArea
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The second of this app's three transports (`docs/03-architecture.md` §2): a plain-HTTP
 * client for the already-built `server/`, scoped to [SYNCED_TYPES] only (see
 * `sync/ServerSync.kt`). Runs as a coroutine loop from `KaAlertoApplication`, the same
 * shape as `family/CircleCheckInNotifier.kt`/`geofence/GeofenceNotifier.kt` — no
 * WorkManager, no foreground service, because an HTTP call is quick and does not need a
 * held-open connection the way `mesh/MeshService.kt`'s Nearby Connections session does.
 *
 * A blank/unset server URL (`SyncPrefs.getServerUrl` returning null) makes every cycle a
 * no-op — a device with no server nearby behaves exactly as it does today, the same
 * every-transport-is-optional property `AndroidManifest.xml`'s own `uses-feature` block
 * already states as a project-wide rule.
 */
class ServerSyncLoop(private val context: Context) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            val repository = EventRepository(KaAlertoDatabase.getInstance(context).eventDao())
            while (isActive) {
                val baseUrl = SyncPrefs.getServerUrl(context)
                if (!baseUrl.isNullOrBlank()) {
                    val pushed = runCatching { pushBatch(baseUrl, repository) }.isSuccess
                    val pulled = runCatching { pullDelta(baseUrl, repository) }.isSuccess
                    if (pushed || pulled) {
                        SyncPrefs.setLastSyncedAtMs(context, System.currentTimeMillis())
                    }
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private suspend fun pushBatch(baseUrl: String, repository: EventRepository) {
        val toPush = eventsToSync(repository.all())
        if (toPush.isEmpty()) return
        postJson(buildBatchUrl(baseUrl), encodeBatchRequest(toPush))
    }

    /** Loops on `hasMore` so a backlog drains in one cycle rather than trickling one page
     * every [SYNC_INTERVAL_MS]. */
    private suspend fun pullDelta(baseUrl: String, repository: EventRepository) {
        val bounds = DemoArea.bounds
        var cursor = SyncPrefs.getCursor(context)
        var hasMore = true
        while (hasMore) {
            val url = buildPullUrl(
                baseUrl,
                minLon = bounds.longitudeWest,
                minLat = bounds.latitudeSouth,
                maxLon = bounds.longitudeEast,
                maxLat = bounds.latitudeNorth,
                since = cursor,
            )
            val page = decodePullResponse(getJson(url)) ?: return
            if (page.events.isNotEmpty()) {
                repository.insert(stampServerOrigin(page.events))
            }
            cursor = page.nextCursor
            SyncPrefs.setCursor(context, cursor)
            hasMore = page.hasMore
        }
    }

    private suspend fun postJson(urlString: String, body: String) = withContext(Dispatchers.IO) {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode < 400) { "POST $urlString failed: ${connection.responseCode}" }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun getJson(urlString: String): String = withContext(Dispatchers.IO) {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            check(connection.responseCode < 400) { "GET $urlString failed: ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** A starting guess, easy to tune once this is running against a real server on
         * a real network — no reason to treat it as fixed. */
        const val SYNC_INTERVAL_MS = 30_000L
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }
}
```

- [ ] **Step 2: Wire it into `KaAlertoApplication.kt`**

Read the current file first — it is short (59 lines). Add an import alongside the existing `com.macci.kaalerto.*` imports:

```kotlin
import com.macci.kaalerto.sync.ServerSyncLoop
```

Then add a line inside `onCreate()`, after the existing `CircleCheckInNotifier(this).start(applicationScope)` line and before the `SosTransmitter(this).start(applicationScope)` line:

```kotlin
        // Build day 13. Plain-HTTP push/pull against server/ — see sync/ServerSyncLoop.kt.
        // A no-op on every device until a server address is entered in Profile.
        ServerSyncLoop(this).start(applicationScope)
```

- [ ] **Step 3: Verify the app compiles and assembles**

Run: `cd android && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew compileDebugKotlin assembleDebug -q`
Expected: succeeds with no output.

- [ ] **Step 4: Run the full unit test suite to confirm no regression**

Run: `./gradlew testDebugUnitTest -q`
Expected: succeeds with no output (all existing tests plus Task 1's new ones still pass).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/macci/kaalerto/sync/ServerSyncLoop.kt android/app/src/main/kotlin/com/macci/kaalerto/KaAlertoApplication.kt
git commit -m "feat(android): server-sync coroutine loop, started from the Application"
```

---

### Task 5: Server address field in Profile

**Files:**
- Modify: `android/app/src/main/kotlin/com/macci/kaalerto/identity/ProfileFields.kt`
- Modify: `android/app/src/main/kotlin/com/macci/kaalerto/identity/ProfileScreen.kt`
- Modify: `android/app/src/main/kotlin/com/macci/kaalerto/ui/KaAlertoApp.kt`

**Interfaces:**
- Consumes: Task 2's `SyncPrefs`.
- Produces: `@Composable internal fun ServerUrlField(serverUrl: String, onServerUrlChange: (String) -> Unit, lastSyncedAtMs: Long?)` in `ProfileFields.kt`; `ProfileScreen`'s signature grows three parameters (`serverUrl: String, onServerUrlChange: (String) -> Unit, lastSyncedAtMs: Long?`).

No unit test — pure UI wiring, matching every other field-addition task in this codebase (e.g. the original `PhoneField` addition, and the family-checkin plan's own Task 6 note: "no new unit tests of its own — it is UI wiring... matching every prior UI-wiring task in this codebase").

- [ ] **Step 1: Add the field composable**

In `android/app/src/main/kotlin/com/macci/kaalerto/identity/ProfileFields.kt`, add these two functions after the existing `PhoneField` composable (right before `HomeSection`):

```kotlin
/**
 * Build day 13 — the server-sync address (`sync/ServerSyncLoop.kt` reads it fresh every
 * cycle, no caching, so a changed address takes effect on the very next tick with no
 * restart). Optional and unvalidated, same posture as [PhoneField]: empty means sync is
 * simply off, not an error state.
 */
@Composable
internal fun ServerUrlField(serverUrl: String, onServerUrlChange: (String) -> Unit, lastSyncedAtMs: Long?) {
    val colors = LocalKaAlertoColors.current
    val serverUrlDescription = tr("Address ng server", "Server address")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(tr("ADDRESS NG SERVER (OPSYONAL)", "SERVER ADDRESS (OPTIONAL)"))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, colors.border)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    capitalization = KeyboardCapitalization.None,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = serverUrlDescription },
            )
            if (serverUrl.isEmpty()) {
                Text(
                    "192.168.1.42:3000",
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.border,
                )
            }
        }
        Text(
            serverSyncStatusLabel(lastSyncedAtMs),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "Huling nag-sync: 18 min ago", or "no sync yet" — never a bad-news state, matching this
 * app's general rule for anything that only ever reports presence or absence, not
 * failure. Deliberately duplicated age-formatting rather than shared with
 * `family/FamilyCircleScreen.kt`'s `checkInAgeLabel` — that function's own doc comment
 * already accepts this same small duplication against `sos/SosShared.kt`'s
 * `elapsedLabel`, which uses an incompatible mm:ss format.
 */
@Composable
private fun serverSyncStatusLabel(lastSyncedAtMs: Long?): String {
    if (lastSyncedAtMs == null) return tr("Wala pang sync", "No sync yet")
    val minutes = (System.currentTimeMillis() - lastSyncedAtMs) / 60_000
    val age = when {
        minutes < 1 -> tr("ngayon lang", "just now")
        minutes < 60 -> tr("$minutes min ang nakalipas", "$minutes min ago")
        else -> tr("${minutes / 60}h ${minutes % 60}m ang nakalipas", "${minutes / 60}h ${minutes % 60}m ago")
    }
    return tr("Huling nag-sync: $age", "Last synced: $age")
}
```

- [ ] **Step 2: Wire the field into `ProfileScreen.kt`**

In `android/app/src/main/kotlin/com/macci/kaalerto/identity/ProfileScreen.kt`, add three parameters to the `ProfileScreen` composable's signature, right after `onPhoneChange: (String) -> Unit,`:

```kotlin
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    lastSyncedAtMs: Long?,
```

Then in the field-rendering `Column`, add the new field right after `PhoneField(phone = phone, onPhoneChange = onPhoneChange)`:

```kotlin
                PhoneField(phone = phone, onPhoneChange = onPhoneChange)
                ServerUrlField(serverUrl = serverUrl, onServerUrlChange = onServerUrlChange, lastSyncedAtMs = lastSyncedAtMs)
```

- [ ] **Step 3: Add the draft state and persistence in `KaAlertoApp.kt`**

Add an import alongside the other `com.macci.kaalerto.*` imports:

```kotlin
import com.macci.kaalerto.sync.SyncPrefs
```

Add a new draft state variable right after the existing `var draftPhone by remember { mutableStateOf(LocalIdentity.registeredPhone(appContext)) }` line:

```kotlin
    var draftServerUrl by remember { mutableStateOf(SyncPrefs.getServerUrl(appContext) ?: "") }
```

In the `is Screen.Profile -> { ... }` branch, add the three new arguments to the `ProfileScreen(...)` call, right after `onPhoneChange = { draftPhone = it },`:

```kotlin
                serverUrl = draftServerUrl,
                onServerUrlChange = { draftServerUrl = it },
                lastSyncedAtMs = SyncPrefs.getLastSyncedAtMs(context),
```

In that same branch's `onSave = { ... }` lambda, add the persistence call right after the existing `LocalIdentity.register(context, draftFirstName, draftLastName, draftPhone, draftBarangay)` line:

```kotlin
                    SyncPrefs.setServerUrl(context, draftServerUrl)
```

- [ ] **Step 4: Verify the app compiles and assembles**

Run: `cd android && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew compileDebugKotlin assembleDebug -q`
Expected: succeeds with no output.

- [ ] **Step 5: Run the full unit test suite to confirm no regression**

Run: `./gradlew testDebugUnitTest lintDebug -q`
Expected: succeeds with no output.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/macci/kaalerto/identity/ProfileFields.kt android/app/src/main/kotlin/com/macci/kaalerto/identity/ProfileScreen.kt android/app/src/main/kotlin/com/macci/kaalerto/ui/KaAlertoApp.kt
git commit -m "feat(android): server address field in Profile, with last-synced status"
```

---

### Task 6: Server-side field-name fix

**Files:**
- Modify: `server/src/db.js`
- Create: `server/test/db.test.js`

**Interfaces:** none new — this corrects an existing bug in `insertEvent`'s field mapping.

- [ ] **Step 1: Write the failing test**

Create `server/test/db.test.js`:

```js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');
const { openDatabase } = require('../src/db');

// A real temp file, not ':memory:' — this test needs to reopen the same database from a
// second connection to inspect a raw column, and two ':memory:' connections never share
// state (server.test.js's own comment notes the same limitation).
test('insertEvent stores expiresAt in the indexed expires_at_ms column, not just the payload', () => {
  const tmpPath = path.join(os.tmpdir(), `kaalerto-db-test-${Date.now()}-${Math.random().toString(36).slice(2)}.db`);
  const db = openDatabase(tmpPath);

  db.insertEvent({
    id: 'test-expiry-event',
    type: 'flood_report',
    lat: 18.17,
    lon: 120.6,
    timestampMs: 1000,
    expiresAt: 5000,
  });
  db.close();

  const raw = new DatabaseSync(tmpPath);
  const row = raw.prepare('SELECT expires_at_ms FROM events WHERE id = ?').get('test-expiry-event');
  raw.close();
  fs.unlinkSync(tmpPath);

  assert.equal(row.expires_at_ms, 5000);
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd server && npm test`
Expected: FAIL — `db.test.js`'s assertion fails because `row.expires_at_ms` is `null` (the current code reads `event.expiresAtMs`, which is `undefined` on the posted object, so `?? null` stores `null`).

- [ ] **Step 3: Fix the field name**

In `server/src/db.js`, inside `insertEvent`, find this line:

```js
        expires_at_ms: event.expiresAtMs ?? null,
```

Change it to:

```js
        expires_at_ms: event.expiresAt ?? null,
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd server && npm test`
Expected: PASS — both `db.test.js`'s new test and every existing test in `server/test/server.test.js` are green (this is a `node --test` run, so both files execute together).

- [ ] **Step 5: Commit**

```bash
git add server/src/db.js server/test/db.test.js
git commit -m "fix(server): insertEvent reads expiresAt, not the nonexistent expiresAtMs field"
```

---

### Task 7: Manual device verification

**Files:** none — this task verifies Tasks 1-6 end to end. No code changes.

This mirrors the design spec's own Testing section, and this project's standing rule that nothing counts as built until proven against a real running server and a real device, matching the same bar every previous build day in this codebase was held to (CLAUDE.md's "Test in airplane mode every single day" — adapted here to "test against a real server every time," since this is the one feature in the whole app that depends on one being reachable).

- [ ] **Step 1: Run the full automated suite one more time on the merged result**

```bash
cd android && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest lintDebug assembleDebug -q
cd ../server && npm test
```

Expected: both succeed with no failures.

- [ ] **Step 2: Start the server on the laptop, find its LAN IP**

```bash
cd server && npm start
```

In a separate terminal, find the laptop's LAN IP (Windows: `ipconfig`, look for the WiFi adapter's IPv4 address — something like `192.168.1.42`). Confirm the server answers from another machine on the same network:

```bash
curl http://<laptop-lan-ip>:3000/health
```

Expected: `{"ok":true,"cursor":0}` (or whatever cursor value if the server's DB file already has data from a previous run — `server/kaalerto.db` persists between restarts unless deleted).

- [ ] **Step 3: Install the current debug build on two devices**

```bash
cd android && ./gradlew installDebug
```

Install on both a physical device and/or emulator — whichever two are available. Both need to be on the same WiFi network as the laptop running the server.

- [ ] **Step 4: Enter the server address and file a report on device A**

On device A: open the drawer → "Ang profile ko" → enter `<laptop-lan-ip>:3000` in the new "Address ng server" field → "I-save". Then file a flood report from the map ("Mag-ulat").

Within `ServerSyncLoop.SYNC_INTERVAL_MS` (30s), confirm the push happened — either by watching the server's own stdout log for the incoming request, or by re-running:

```bash
curl http://<laptop-lan-ip>:3000/health
```

Expected: the `cursor` value has advanced past what it was after Step 2.

- [ ] **Step 5: Confirm the pull path reaches a device that never saw the report any other way**

On device B (a fresh install, never paired with device A over mesh, same server address entered in its own Profile): wait one sync interval, then open the map.

Expected: device A's report appears on device B's map, with its severity marker in the correct location — proving the pull path reaches a device the mesh never could.

- [ ] **Step 6: Confirm dedup — no duplicate marker on re-sync**

Force-stop and relaunch device A (or just wait through a few more sync cycles).

Expected: the report still appears exactly once on both devices' maps — the server's unique constraint on `id` and Room's own `OnConflictStrategy.IGNORE` both dedup for free, so re-pushing/re-pulling the same event never doubles it.

- [ ] **Step 7: Confirm a blank server URL is a true no-op**

On device A: open Profile, clear the "Address ng server" field, save.

Expected: no crash, and the server's own log shows no further requests from that device (watch it for one full sync interval).

- [ ] **Step 8: Confirm resilience to the server restarting**

Re-enter the server address on device A. Kill the server process (`Ctrl+C` in its terminal), wait a few seconds, then restart it (`npm start` again).

Expected: no crash on either device during the outage; once the server is back, the next scheduled sync cycle succeeds with no manual action needed on the phone (confirmed via the Profile screen's "Huling nag-sync" / "Last synced" line advancing again, or the server's log resuming).

- [ ] **Step 9: Update CLAUDE.md**

Add an entry to CLAUDE.md's "Current state" section recording that build day 13's server sync is done and what was verified — matching the voice and level of detail every other build-day entry in that file already uses (what works, what's still not built — HTTPS, FCM push, per-region cursors — and any surprises found during the manual pass). This is documentation, not code; no test applies to this step.
