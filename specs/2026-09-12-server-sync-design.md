# Server sync — design

Status: approved, not yet implemented. This is build day 13's "server sync"
item, previously scoped and deliberately deferred (CLAUDE.md, 8 Sep pass:
"new client integration... not a bugfix on existing behavior"). The server
side (`server/`) already exists and is tested; this spec covers only the
Android client that talks to it, plus one small server-side fix.

## Why this exists

Reports and official markings today only reach another device by riding
the Bluetooth/Wi-Fi Direct mesh (`mesh/MeshService.kt`) from phone to
phone. A report a resident files has no way to reach anyone outside
direct or relayed mesh range. Server sync is the second of the three
transports `docs/03-architecture.md` §2 describes (server, mesh, SMS) —
the one that makes a report reachable by *any* device that can reach the
same server, not just ones within relay range at the right moment.

## Source of truth

- `server/src/server.js` / `server/src/db.js` — the already-built,
  already-tested server. `POST /events/batch` (idempotent on event id,
  per-event accept/reject/duplicate, returns a global cursor) and
  `GET /events?bbox=minLon,minLat,maxLon,maxLat&since=<cursor>&limit=<n>`
  (bbox-required, delta pull, `{events, nextCursor, hasMore}`). No auth,
  no crypto, no signing — matches ground rule 4, and this spec does not
  change that.
- `docs/03-architecture.md` §2.2 describes the full production vision
  (HTTPS, CBOR, zstd, cert pinning, FCM wake-ups, per-region cursors,
  signed events). None of that is what the server actually implements —
  it took the same hackathon-scale simplifications the rest of this
  project's ground rules call for. This spec calibrates the client to
  the server that actually exists, the same way the server itself
  already diverged from the doc's full vision. Every simplification
  below versus §2.2 is intentional, not an oversight.
- CLAUDE.md's 8 Sep note flags the one concrete bug already known: the
  server's `insertEvent` reads `event.expiresAtMs` off the posted JSON,
  but Android's `Event.kt` field is `expiresAt` — the indexed
  `expires_at_ms` column silently stays null for every event this client
  posts. Bundled into this work (see Server-side fix, below).

## Non-goals (explicitly out of scope for this pass)

- **Not every event type.** Only `flood_report`, `confirm`, `dispute`,
  `official_status` sync to the server. `sos*`, `family_checkin`,
  `circle_invite`, and `role_*` events stay mesh-only. The server has no
  auth and is queryable by anyone who can reach it (`GET /events?bbox=…`
  returns everything for that box, no access control) — SOS medical
  detail and the family-circle pairing graph are exactly the content
  this project has already gone out of its way to keep off an
  unrestricted channel (`sos/SosMeshPolicy.kt`'s redaction,
  `family/CircleEvents.kt`'s disclosed mesh residual). Widening sync to
  those types would be a real privacy step down and needs its own
  explicit decision later, not a side effect of this pass.
  **The `SYNCED_TYPES` filter is symmetric — applied on pull as well as
  push.** The server also has no *write* auth: `POST /events/batch`
  accepts anything from anyone, by design (ground rule 4). An
  unfiltered pull would mean any device that can reach the server —
  not just one physically nearby, unlike the mesh — could inject
  `sos`/`role_*` events into every syncing device, including ones that
  fire the alarm-stream, DND-bypassing SOS notification channel
  (`sos/SosAlertWatcher.kt`). Filtering only the outbound side would
  have closed the read-privacy gap while leaving this write-injection
  gap wide open, so `sync/ServerSyncLoop.kt`'s `pullDelta` runs pulled
  events through `eventsToSync(...)` before ever calling
  `repository.insert(...)`, the identical filter `pushBatch` already
  applies going the other way.
- **No HTTPS, no certificate pinning, no signing.** The demo server runs
  on a plain-HTTP laptop on the local network. A scoped cleartext
  exception (below) is the extent of the transport security work here.
- **No push wake-ups (FCM).** `docs/03-architecture.md` §2.2 itself
  frames push as "an *optimisation* — the device syncs on schedule and
  on app open regardless" — this build only needs the fallback-safe
  default it already describes, not the optimization.
- **No per-region pull cursor.** One global `since` cursor, matching the
  server's own single global cursor (`db.currentCursor()`) — there is
  exactly one demo region.
- **No push-side cursor or "already uploaded" bookkeeping.** See
  Architecture — this is deliberate, not a deferred nice-to-have.
- **No dedicated sync-status UI** (no header line, no spinner). The
  existing "May koneksyon" map-header text is generic connectivity, not
  sync-specific, and is not touched by this work. A last-synced
  timestamp in Profile (see UI) is the only visible surface.

## Architecture

**New file `sync/ServerSync.kt`** — the whole client, deliberately not
folded into `EventRepository` itself: sync is one more consumer of the
repository (same relationship `MeshService` already has to it), not a
capability the repository needs to know about.

**HTTP: `HttpURLConnection` + `kotlinx.serialization`, no new
dependency.** The surface is one POST and one GET, both plain JSON. This
project has added exactly one dependency for a capability it didn't
already have a way to do (`zxing-android-embedded`, for a real missing
camera-scanner capability) and otherwise holds the line hard (Express is
the *only* npm dependency on the server; the whole point of `node:sqlite`
over `better-sqlite3` was avoiding a native module). A JSON-over-HTTP
client for two endpoints does not clear that bar — OkHttp would be
pleasant to write against but isn't solving a problem `HttpURLConnection`
can't.

**Push (device → server): full resync every cycle, no push cursor.**

```kotlin
private val SYNCED_TYPES = setOf("flood_report", "confirm", "dispute", "official_status")

suspend fun pushBatch(baseUrl: String, events: List<Event>) {
    val toPush = events.filter { it.type in SYNCED_TYPES }
    if (toPush.isEmpty()) return
    // POST { events: [...] } to $baseUrl/events/batch
    // Body is exactly the Event list, kotlinx.serialization-encoded —
    // Event is already @Serializable (it *is* the wire format, same
    // reasoning as the mesh transport) and the server's own validateEvent
    // only requires id/type/lat/lon, everything else round-trips through
    // `payload` untouched.
}
```

Every sync cycle reads **all** locally-held events matching
`SYNCED_TYPES` — mesh-received ones included, not just self-authored —
and posts the whole set. No local "have I pushed this id already"
tracking. Two reasons, not one:

1. **It's what carry-forward requires.** `docs/03-architecture.md` §2.3.5:
   "Any device with a connected data path uploads *all* mesh-acquired
   events it holds, not just its own." A device that relayed someone
   else's report five hours ago, with no connectivity at the time, must
   still upload it the next time it *does* reach the server — a
   timestamp-based "only push things newer than X" cursor would
   silently break exactly this case, since the relayed event's own
   `timestampMs` is old even though this device only just received it.
2. **The server already makes it free.** `INSERT OR IGNORE` on the
   event's primary key means re-posting something the server already
   has costs one indexed lookup and a `'duplicate'` status in the
   response — and Room's own `EventDao` insert is `OnConflictStrategy.IGNORE`
   too, so the same reasoning applies symmetrically on pull. At
   hackathon event volumes (tens to low hundreds, not thousands) over a
   local WiFi link, resending unchanged events every cycle costs nothing
   that matters. This is the same trade this codebase already makes
   everywhere else derived state is involved — `effectiveCircle`,
   `circleStatuses`, `Reducer.summarizeAll` all recompute from scratch on
   every call rather than maintaining incremental state, because the
   recompute is cheap and the incremental version is where the bugs
   hide.

**Pull (server → device): cursor-tracked, bbox-scoped to the demo area.**

```kotlin
suspend fun pullDelta(context: Context, baseUrl: String) {
    val bounds = DemoArea.bounds  // LatLngBounds — the only region this build ever needs
    var cursor = SyncPrefs.getCursor(context)
    do {
        // GET $baseUrl/events?bbox=${bounds.longitudeWest},${bounds.latitudeSouth},
        //     ${bounds.longitudeEast},${bounds.latitudeNorth}&since=$cursor
        val page = fetchPage(baseUrl, bounds, cursor)
        val stamped = page.events.map { it.copy(origin = "server") }
        repository.insert(stamped)  // OnConflictStrategy.IGNORE — dedup is already free here
        cursor = page.nextCursor
        SyncPrefs.setCursor(context, cursor)
    } while (page.hasMore)
}
```

`origin = "server"` is a new value — nothing in the codebase has used it
before (grep confirms `origin` is only ever `"local"` or `"mesh"` today).
Parallel to `MeshProtocol`'s `origin = "mesh"` stamp on relay: it records
*how this device learned about the event*, not a claim about the
event's history before that. `hopCount` is left as whatever the payload
already carried — hop count is a mesh-specific measure and a server pull
isn't a hop in that sense.

**`sync/SyncPrefs.kt`, same shape as `HomeLocationStore`.** One
SharedPreferences file holding everything this feature needs locally:
`getCursor`/`setCursor` (`Long`, default `0`), `getServerUrl`/
`setServerUrl` (`String?`, default `null`), `getLastSyncedAtMs`/
`setLastSyncedAtMs`. The server URL lives here rather than in
`identity/`'s prefs, even though `ProfileScreen` is what edits it —
`HomeLocationStore` already sets this precedent: it lives in `geofence/`
next to `GeofenceNotifier`, its actual consumer, even though Onboarding
and Profile are what write to it. No Room table, no new schema.

**Trigger: a coroutine loop from `KaAlertoApplication`, same shape as
`CircleCheckInNotifier`/`GeofenceNotifier`.**

```kotlin
class ServerSyncLoop(private val context: Context) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                val baseUrl = SyncPrefs.getServerUrl(context)
                if (!baseUrl.isNullOrBlank()) {
                    runCatching { pushBatch(...) }
                    runCatching { pullDelta(...) }
                }
                delay(SYNC_INTERVAL_MS) // 30s
            }
        }
    }
}
```

Runs once immediately on start, then every 30s while the process is
alive — no WorkManager, no foreground service. HTTP calls are quick and
don't need a held-open connection the way Nearby Connections does; the
mesh's own foreground-service requirement doesn't apply here. A blank
server URL (the default) makes both `runCatching` blocks no-ops — a
device with no server nearby behaves exactly as it does today, same
"every transport is optional" property the manifest's own `uses-feature`
comment already states as a project-wide rule.

**Failure handling: `runCatching`, log and move on, no retry/backoff
inside a cycle.** A failed push or pull just waits for the next 30s tick
— the loop itself *is* the retry. No circuit breaker, no distinguishing
"no network" from "server erroring" (§2.2's fuller vision wants this;
this build's failure mode is "nothing happened this cycle, try again in
30s," which is enough at this scale and consistent with never showing a
fake-progress spinner per §6.4.4).

## Server-side fix (bundled, not separate scope)

`server/src/db.js`'s `insertEvent`: `event.expiresAtMs` → `event.expiresAt`,
matching `Event.kt`'s actual field name. One line. Doesn't change the
round-tripped `payload` (the full posted JSON is already stored and
returned verbatim regardless of the indexed columns), so this isn't a
behavior change for anything that already works — it just makes the
`expires_at_ms` column stop silently staying null.

## Cleartext traffic exception

**Correction from the original design pass:** Android's network security
config `<domain>` element only matches an exact or wildcard *hostname* —
there is no schema construct for an IP/CIDR range, so "scope the exception
to private-LAN address ranges" (this section's original text) is not
actually expressible. Since the server's address is whatever the user
types into Profile at runtime, and a static manifest-compiled XML
resource cannot be scoped to a value that doesn't exist until then, there
is no way to scope this narrower than app-wide.

New `res/xml/network_security_config.xml` with a single
`<base-config cleartextTrafficPermitted="true">`, referenced from
`AndroidManifest.xml`'s `<application android:networkSecurityConfig=...>`.
This is a real, disclosed widening — every cleartext connection the app
might ever make is now OS-permitted, not just the sync one — but the
*practical* exposure stays bounded by what the code actually does: the
only cleartext call this app makes is `sync/ServerSync.kt`'s POST/GET to
the one URL a user typed in, and every other network call already in this
app (tile loading, geocoding) already uses HTTPS regardless of what this
policy allows. Documented plainly in the XML file's own comment rather
than left implicit. `INTERNET`/`ACCESS_NETWORK_STATE` permissions already
exist in the manifest; no permission change needed.

## Config / UI

**New field in `identity/ProfileFields.kt`**: `ServerUrlField`, same
visual shape as `PhoneField` (label, `BasicTextField`, hint text,
explanatory caption) — "Server address (opsyonal)" / "Server address
(optional)", hint `"192.168.1.42:3000"`. `ProfileScreen.kt` reads/writes
it through `SyncPrefs.getServerUrl`/`setServerUrl` directly (the
composable itself just takes a value + `onValueChange`, same as every
other field in that file) — `ServerSyncLoop` reads the same prefs fresh
every cycle rather than caching it, so a changed address takes effect on
the very next tick with no restart.

**Last-synced line, directly under the new field**: "Huling nag-sync:
18 min ago" / "no sync yet" — mirrors `checkInAgeLabel`'s age-string
pattern already used in `FamilyCircleScreen`. Backed by
`SyncPrefs.getLastSyncedAtMs`, written by `ServerSyncLoop` on every
successful cycle (push or pull, whichever completed). This is the entire
UI surface for this feature — no header changes, no new screen.

## Testing

**Unit tests** (JVM, no device needed — `HttpURLConnection` calls
mocked/faked at the boundary, same as this project's existing pattern of
keeping pure logic separately testable from Android framework calls):

- `SYNCED_TYPES` filtering: a `flood_report`/`confirm`/`dispute`/
  `official_status` event is included in a push batch; a `sos`/
  `family_checkin`/`role_grant` event is excluded.
- Pull response mapping: a fetched event gets `origin = "server"`
  regardless of what origin it carried in the response; `hopCount`
  passes through unchanged.
- Cursor advances to `nextCursor` after a successful page; a page with
  `hasMore = true` triggers a second fetch with the new cursor; a
  network failure mid-loop leaves the cursor at its last successfully
  advanced value (never advanced past a page that wasn't inserted).
- A blank/null server URL results in zero HTTP calls from
  `ServerSyncLoop`'s cycle body.

**Manual device verification** (real hardware or emulator, on the same
network as a locally-running `server/`, matching how the server was
already smoke-tested per CLAUDE.md's 8 Sep note):

1. Start `server/` on a laptop (`node server/src/server.js` or however
   BUILD_TASKS.md already documents running it), note its LAN IP.
2. On device A: enter the server address in Profile, file a flood
   report. Confirm (via the server's own `GET /events?bbox=…` from a
   browser or curl, or `GET /health`'s cursor advancing) that it reached
   the server within one sync interval.
3. On device B (fresh install, never connected to device A over mesh,
   same server address entered): confirm device A's report appears on
   device B's map within one sync interval — proving the pull path
   reaches a device the mesh never could.
4. Confirm a second post of the same report (e.g., relaunching device A)
   doesn't duplicate the marker — dedup on both the server's `id` unique
   constraint and Room's `OnConflictStrategy.IGNORE`.
5. Clear the server URL on device A mid-session; confirm no crash and no
   further network calls (check the server's own request log staying
   quiet for that device).
6. Kill and restart the laptop's server process; confirm the app doesn't
   crash on a failed cycle and resumes syncing once the server is back,
   with no manual action on the phone.

## Files touched (new)

- `sync/ServerSync.kt` — `pushBatch`, `pullDelta`, `SYNCED_TYPES`.
- `sync/SyncPrefs.kt` — cursor + last-synced-at persistence.
- `sync/ServerSyncLoop.kt` — the coroutine loop, started from
  `KaAlertoApplication` alongside `CircleCheckInNotifier`.
- `identity/ProfileFields.kt` — add `ServerUrlField` composable,
  following `PhoneField`'s exact shape.
- `identity/ProfileScreen.kt` — wire the new field + last-synced line
  into the existing field list (same place `PhoneField` already sits).
- `res/xml/network_security_config.xml` — new, private-LAN cleartext
  scope.
- `AndroidManifest.xml` — one attribute added to `<application>`
  (`android:networkSecurityConfig`). No new permissions.
- `server/src/db.js` — one-line field-name fix (`expiresAtMs` →
  `expiresAt`).

## Open questions for implementation time (not blocking design approval)

- The 30s sync interval is a starting guess, easy to tune once it's
  running against a real server on a real network — no reason to treat
  it as fixed.
