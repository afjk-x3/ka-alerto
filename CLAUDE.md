# KaAlerto — project context

Offline-first community flood map and rescue channel for Philippine barangays. Android (Kotlin), sideloaded. Solo developer. Hackathon: 1–30 September 2026, five equally-weighted staged submissions.

**Read `docs/02-prd.md` before proposing product changes.** It is the canonical spec — 17 sections, 43 requirement IDs, ~10 pages.

---

## Current state (updated 6 Sep 2026)

**The demo area is frozen: Barangay San Juan Bautista, San Nicolas, Ilocos Norte.** Every fixture, screenshot and route lives inside `DemoArea.kt`'s bounding box. Read that file's class doc before touching any coordinate in the project — it records the sourcing (two independent centroid sources agreeing within ~300 m, no official boundary polygon exists anywhere, and a landmark cluster ~1.3 km west was deliberately excluded because it is plausibly a *different* barangay's poblacion). **None of this has been walked or verified against a printed barangay map** — confirm with someone who knows the area before it goes anywhere near the barangay itself.

**Day 0 fixtures are now fully done, including the OSM tile extract.** `android/app/src/main/assets/` has real, OSM-sourced seed reports (19, across S0–S3, including the deliberate conflicting pair for the Rule C/SX demo), evacuation centres (4 real named facilities, capacity figures explicitly marked as placeholders), and route GeoJSONs (3 real street centrelines). `tools/osm-extract/demo-area.osm.pbf` (fetched 5 Sep 2026) is the region-clipped OSM extract for the offline tile pipeline — 10,239 nodes, 1,787 ways, 3 relations, verified against the same real streets and landmarks the other fixtures reference. It came from the official OSM API's `/api/0.6/map` bbox endpoint rather than a Geofabrik PH download clipped with `osmium`/`osmconvert`/`ogr2ogr` (none of which were installed, and the PH extract alone is ~600 MB) — same bbox-clipped `.osm.pbf` result, at the cost of only `pip install osmium` for the XML→PBF conversion. See `tools/osm-extract/README.md`. This is still just the source extract, not a built tile pack — the day 1 MBTiles-fallback build step is separate and not needed since `OfflineManager` already works (below).

**Build day 1 (offline map tiles) is DONE and verified — on the API 34 emulator, not API 37.** `OfflineMapPack.kt` and `MapScreen.kt` implement the `OfflineManager` pre-download path. Full DoD confirmed by screenshot: airplane mode on, force-stop, cold relaunch, map renders the real demo area (`San Juan Bautista` label, Sotto Street, evacuation centres) from the 8-tile offline pack, zero network. Style is OpenFreeMap's Liberty (`tiles.openfreemap.org`) — not yet the self-hosted OSM-derived style the architecture doc calls for, but real cartographic detail with worldwide maxzoom-14 coverage, not the bundled-MBTiles fallback (not needed; `OfflineManager` works fine).

**Build day 2 (event model + local store) is DONE and verified — same `API34_Test` emulator.** Room schema for `Event` and `FeatureState` (`android/.../data/`), an `EventRepository` deduplicating by content-hash primary key, a seed loader for `assets/seed_data.json`, and severity-colored map markers wired into `MapScreen` via a new `MapViewModel`. DoD confirmed by screenshot: airplane mode, force-stop, cold relaunch, all 19 seeded reports render at the correct real-world locations in the correct severity colors (amber S1, orange S2, red S3, blue S0), zero network. `FeatureState` is schema-only so far — nothing populates it yet; the reducer (Rules A–D, SX conflict detection) is day 4's job, and the seeded conflicting pair currently just renders as a single S3 dot with S0 occluded underneath.

**Build day 3 (reporting flow) is DONE and verified — same `API34_Test` emulator, airplane mode, cold relaunch.** `report/` holds the whole flow: `WaterLevel.kt` (body/vehicle depth scales — body copy and severity mapping lifted verbatim from `design/artboards/Report-Normal.dc.html`'s own embedded data), `WaterLevelIllustration.kt` (Canvas-drawn dynamic illustration, since the design's is SVG specific to the design-canvas host), `ReportScreen.kt`, `ReportSubmit.kt`. `location/LocationFetcher.kt` does one-shot GPS via `FusedLocationProviderClient`, falling back to a tap-to-pick mode on the existing map (`map/MapScreen.kt` gained a FAB and a pick-mode click listener — no second `MapView`). Both the GPS path and the map-tap fallback were exercised end to end: a filed report writes to Room immediately and appears on the map with no extra plumbing, since the map already observes the event table as a `Flow`. **`identity/LocalIdentity.kt` is a stopgap** — no `BUILD_TASKS.md` day schedules the real registration/onboarding screen (PRD §9) yet, so every locally-authored report currently carries a generated placeholder name until that screen exists.

**One MapScreen refactor rode along with day 3, not a day 3 requirement itself:** the `AndroidView` `update` block used to call `map.setStyle(...)` on every recomposition (reloading the whole style each time the event list changed, a harmless but wasteful pattern from day 1/2). It now guards on a remembered `MapLibreMap` reference and calls `setStyle` once; markers push reactively via a `LaunchedEffect` instead. Re-verified the day 1/2 airplane-mode DoD after this change — still passes.

**Build day 4 (confirm/dispute + reducer — "the intellectual core") is DONE and verified — same `API34_Test` emulator, airplane mode, cold relaunch.** `data/Reducer.kt` is a pure fold over a feature's events implementing a deliberately simplified reading of `docs/03-architecture.md` §4-5 (role-based weight, not signed certificates — no crypto per ground rule 4): Rules A-D, Wilson-score confidence, and the four buckets. Map markers (`map/EventMarkers.kt`) now render one per *feature* (reducer output), not one per raw event as in day 2 — the seeded conflicting pair renders as a distinct **SX** marker, confirmed by screenshot. `detail/DetailSheet.kt` (tap a marker to open) shows the day-4 detail sheet, copy and layout lifted from `design/artboards/DetailConfirmed-Normal.dc.html`/`DetailConflict-Normal.dc.html`; confirm/dispute actions (`detail/ConfirmDisputeSubmit.kt`) write straight to Room and the whole chain — insert → Flow → reducer → recomposition — updates the open sheet live with no manual refresh. Verified: confirming a seeded S2 report moved its bucket Unverified → Likely in front of the camera; disputing with "cleared now" correctly wrote S0 but did not by itself flip the display (Rule B, one dispute isn't enough). New reports (day 3's `ReportSubmit.kt`) now get a geohash-8 `featureRef` instead of `null`, since the reducer needs something to group same-spot reports by. `ui/theme/SeverityColors.kt` (consolidated in day 3) gained an `SX` entry rather than letting the conflict fall back to a generic "unknown" grey. **That purple is not what the map marker or the detail badge actually draw** — both use a red/amber hazard-stripe treatment, and the `#6A1B9A` shows up only on the official screen's "NGAYON SA MAPA" bar. Earlier revisions of this file described a "purple SX marker"; that was never true on screen, so do not go looking for it.

**Build day 5 (notifications + filters) is DONE and verified — same `API34_Test` emulator, airplane mode, cold relaunch.** Long-press the map to draft a home location (`geofence/HomeLocationStore.kt`), adjust a 100-1000 m radius on a live slider with a real geographic circle drawn on the map (`map/GeofenceCircle.kt` — a generated polygon, not `CircleLayer`'s pixel-space radius, which doesn't scale correctly with zoom), save it. `geofence/GeofenceNotifier.kt` runs for the app's process lifetime (there's no foreground service yet — day 6-7's mesh service is the eventual right home for this), diffing the event table's own `Flow` for newly-appeared reports and firing a local notification (`notification/FloodNotifier.kt`, two channels by severity, S3 bypasses DND) when one lands inside the saved radius. Verified end to end: filed a report 33 m from a saved home point, in airplane mode, after a cold relaunch — notification fired immediately, zero network. The map also gained a filter bar (`map/FilterBar.kt`: severity toggles + recency chips, live-filtering markers) and a manual Storm Mode toggle (`MainActivity.kt` now owns the dark/light state, passed down to `KaAlertoTheme` — deliberately not `isSystemInDarkTheme()`, since Storm is a declared condition per the PRD, not a phone setting).

**Build days 6-7 (Nearby Connections mesh) are CODE COMPLETE but the DoD is NOT MET — and the distinction matters more here than on any previous day.** `mesh/` holds the whole thing: `MeshService.kt` (foreground service, `connectedDevice` type, low-importance persistent notification with a stop action, advertising + discovery on `Strategy.P2P_CLUSTER`, auto-accept), `MeshProtocol.kt` (the JSON wire format — an *anti-entropy* exchange, where peers trade event-ID manifests and send only the diff, batched by measured size against Nearby's 32 KB payload cap), `MeshRadios.kt` and `MeshPermissions.kt`. Received events are stored with `origin = "mesh"` and `hopCount + 1`, and whatever was genuinely new to this device is re-shared to every *other* connected peer — that is the multi-hop leg, and it terminates because a report coming back round a cycle is already known, so nothing is stored and nothing is forwarded. `Event` gained `@Serializable` (the row *is* the wire format — a replicated table needs no transport DTO), `EventDao` gained one-shot `allIds()`/`all()` for the diff, and the map header gained a peer-count line. **Do not describe the mesh as working.** It has never moved a report between two devices: that needs the three physical phones, which are still the blocking setup item (`SETUP_CHECKLIST.md`). What *was* verified on `API34_Test`, in airplane mode after a cold relaunch: the service starts as a foreground service, Nearby accepts both advertising and discovery, the peer counter renders in both Normal and Storm, and the radio watcher behaves. 16/16 unit tests and lint pass.

**Two things the emulator caught on day 6 that would otherwise have surfaced on demo day — both fixed, do not revert:**

1. **Nearby also needs `ACCESS_WIFI_STATE` and `CHANGE_WIFI_STATE` in the manifest**, on top of the Bluetooth and `NEARBY_WIFI_DEVICES` permissions that were already there from day 0. It needs them for the Wi-Fi Direct/hotspot legs it uses to upgrade a Bluetooth connection, even though this app never touches Wi-Fi itself. Both are install-time, so nothing prompts, and nothing fails at build or install time — `startAdvertising()` just fails at runtime with `MISSING_PERMISSION_CHANGE_WIFI_STATE` (8033).
2. **`startAdvertising()` succeeding does not mean the mesh can reach anyone.** Nearby brings up whichever mediums it can and still calls back successfully with the useful ones dead — observed directly, with Wi-Fi LAN advertising failing underneath a successful callback. So `MeshRadios.kt` checks the Bluetooth adapter and location *services* (not just the permission — BUILD_TASKS.md days 6-7 flags that exact confusion) and `MeshService` watches `BluetoothAdapter.ACTION_STATE_CHANGED` / `LocationManager.MODE_CHANGED_ACTION`. Two consequences, both deliberate: the header says "Buksan ang Bluetooth para sa mesh" rather than a false "Naghahanap ng kalapit na phone", and switching Bluetooth back on after airplane mode restores the mesh in about a second with **no app restart** — which is precisely the manual step the day 6-7 debugging note warns the demo depends on.

**Build day 8 (SOS flow) is DONE and the DoD is MET — verified on `API34_Test`, airplane mode, cold relaunch.** `sos/` holds the whole path (SOSHold → SOSContext → SOSStatus → RescueCard), wired through `nav/Screen.kt`, and the map's action bar finally gets the artboard's red SOS button that was deliberately omitted on day 5 because nothing was behind it. Held the button, the request was written at t+0 before any screen asked for context, `QUEUED → BEACONING → UNREACHABLE` ran on real signals, and the rescue card raised *itself* at the 30 s threshold — the card is a state, not a tap. **The QR was decoded straight out of the device screenshot** using ZXing and returned the exact payload the app wrote, including the answers entered on device; that closes `design/README.md`'s long-standing "rescue-card QR is a drawn placeholder" item *for the app* (the artboard's own QR is still decorative). 34/34 unit tests and lint pass.

**Four things about day 8 that are easy to undo by accident:**

1. **SOS state transitions are events, not a column.** `SosState.rank` increases strictly along every arrow in `docs/03-architecture.md` §6.2, and `mergeSosState` folds monotonically — that is what makes two devices holding the same events agree (NFR-4) no matter what order the mesh delivers them in. `EXPIRED` is deliberately absent: it is a server-side escalation decision, and a state nothing can ever set is a lie in an enum.
2. **Two of the three escalation channels do not exist, and the status screen says so.** Only the mesh row is real (it reads the live `MeshStatus`). Server sync is day 13, SMS is day 12, so those rows render dimmed as "Hindi pa gawa", naming the build day. `RELAYED` is likewise never set even when peers are connected — "a peer stored it" is a claim only the peer can make, and the acknowledgement carrying it is day 9. `SosState.reachableInThisBuild` marks what this build can drive and a unit test guards it. Do not "fill these in" with plausible-looking progress; §6.4.4 is explicit — never a spinner, never a fake success.
3. **The rescue card raises itself once per request, not once per composition.** `KaAlertoApp` tracks `rescueCardRaisedFor`. Keying the auto-raise on the state alone made the card's "Bumalik" a trap: it returned to the status screen, the effect re-fired on the unchanged `UNREACHABLE` state, and the user was bounced straight back with no way to reach "Ligtas na ako". Found on device, not in review.
4. **`Event.payload` (`String?`) and DB version 3.** Structured type-specific JSON for events that do not fit the columns — today only the `sos*` family. It rides the mesh as part of the event with no extra handling. The `sos*` events carry `featureRef = null` on purpose: the map's reducer groups on `featureRef`, and a non-null one would put a flood-severity marker on the requester's house. A unit test asserts an SOS never becomes a map marker.

**New dependency: `com.google.zxing:core` 3.5.3** — the pure-Java half, deliberately not `zxing-android-embedded` (which drags in a camera scanner UI this build never needs). Pure Java is why the encoder runs in unit tests, so the QR is decoded and asserted rather than eyeballed. The card draws the module grid on a Compose Canvas at whole-pixel module sizes: a QR scaled by a fractional factor gets soft module edges, and soft edges are what makes a code that decodes perfectly in a test fail against a real camera.

**One audit fix was wrong and has been reverted — do not reapply it.** A full-system audit (`AUDIT-REPORT.md`, untracked) changed `FeatureStateDao.upsert` from `OnConflictStrategy.REPLACE` to `IGNORE`, reasoning from the append-only rule. That rule is about the `events` table. `feature_state` is the *materialized fold* of it, keyed on `featureRef` and rewritten every time the reducer runs — `IGNORE` pins each feature to whatever it happened to be the first time it was seen and silently drops every later recomputation, so a road that rose S1 → S3 would stay S1 in that table forever. The audit's other three fixes (TTL cleanup on cold start, an absolute `UNREACHABLE` deadline, and a mesh error state on radio-off) are correct and stay — but **the TTL one needed a grace period, which it now has.** Deleting at `expiresAt < now` was subtly wrong for the same reason: the reducer derives `isStale` as `now > max(expiresAt)`, so purging at the instant of expiry meant a stale road *vanished* on the next cold start instead of rendering as the grey "Luma na — kailangang tingnan" marker the map legend advertises. Confirmed on device — the seed set dropped from 19 markers to 16 and the dashed-clock stale marker disappeared. `EventRepository.RETENTION_AFTER_EXPIRY_MS` (24 h) is the gap between expiring and being forgotten; both came back after the fix.

**Build day 9 (SOS over mesh + acknowledgement) is CODE COMPLETE; the DoD needs two phones and is NOT met.** `sos/` gained the receiving half: `SosAlertNotifier`/`SosAlertWatcher` (critical alert on its own channel — alarm stream, own vibration pattern, red, `CATEGORY_ALARM`), `SosNearbyScreen` (a resident's coarse view), `SosQueueScreen` (the responder queue with both artboard buttons), and `SosMeshPolicy` (below). An acknowledgement is just a `sos_state` event, so it rides the day 6-7 mesh back to the originator with no new transport code and no server. Verified end to end on `API34_Test` in airplane mode by injecting a peer's SOS into the store exactly as `MeshService` would have written it: alert fired → tapped → resident coarse view → became responder → queue showed exact location and **no medical chip** → acknowledged → `sos_state · role=responder · state=EN_ROUTE` in the DB, ready to relay. 45/45 unit tests and lint pass. **The radio hop itself is still unproven** — same blocking item as days 6-7.

**The day-9 privacy decision — do not undo it.** `SOSNearby.dc.html` promises a resident that relayed SOS detail is unreadable to them, which is `docs/03-architecture.md` §6.5's *encrypted* relay payload. Ground rule 4 forbids crypto and the mesh relays whole `Event` rows, so medical needs and the requester's name would have sat readable on every phone in the barangay — the RA 10173 exposure the PRD flags. `sos/SosMeshPolicy.kt` resolves it by **removing rather than encrypting**: `redactForMesh` strips medical detail and the requester's display name on the way out, irreversibly at the first hop, because what is not sent cannot be read off a relay. Coordinates, timestamp, hop data and the people count still travel — exactly what `QueueVolunteer.dc.html`'s footer says a volunteer is entitled to. The *responder's* name is deliberately kept ("Papunta na si Boy"). **Residual, stated on screen rather than hidden:** coordinates travel in the clear; the resident view coarsens them to a dashed circle and a 50 m-rounded distance, but that is display, not guarantee. The artboard's "hindi mo ito kayang basahin" line is changed in the app to say what is actually true.

**Three more day-9 things that are easy to break:**

1. **`MainActivity` is `launchMode="singleTop"` and the alert's PendingIntent sets `FLAG_ACTIVITY_SINGLE_TOP`.** Without both, tapping the alert while the app is already open resumes it with no callback at all, and the alert silently does nothing. Found by tapping it on the emulator.
2. **The SOS routes must not bounce on an unloaded snapshot list.** `SosViewModel.snapshots` starts empty and fills on the first Flow emission; the sosId-addressed screens used to read that empty list as "this request does not exist" and jump back to the map before a frame drew — which is why the alert appeared to do nothing even once routing was correct. They now wait for the fold (`snapshotsLoaded`).
3. **`SosAlertWatcher` deliberately does not skip its first emission**, unlike `GeofenceNotifier`. A phone that rebooted mid-flood must still shout about a neighbour who called ninety seconds ago — the app restarting is not evidence the emergency ended. It alerts on startup only for requests younger than `FRESH_ON_STARTUP_MS` (10 min).

**Responder mode was a labelled demo toggle on day 9; it is a real application now** (see the 6 September role rebuild below — the button on this screen files a `role_request` and routes to the role screen rather than self-granting). It lives on the nearby-SOS screen where the artboard already has "Magparehistro bilang responder", and its subtext says in as many words that the barangay activates this in the real product. Day 10 is the real role work.

**Build day 10 (official role + evacuation centres) is DONE and the DoD is MET — verified on `API34_Test`, airplane mode.** Three roles (`identity/LocalIdentity.kt`, `RoleScreen.kt`) reached from a role badge in the map header; `official/` posts an official status per feature; `evac/` folds the static fixture plus official updates into a nearest-first list with square shelter pins on the map. 56/56 unit tests and lint pass.

**The day-10 second-official gate — the most load-bearing rule added since day 4's reducer.** `OfficialReverse.dc.html` says a single official cannot lower the severity of a contradicted spot; this day's DoD says one official clears a conflicting road. Both hold now, because the gate is conditional: it applies **only when lowering**, and **only when ≥`DEESCALATION_COUNT` residents are currently reporting worse** (currently = weight above `WEIGHT_FLOOR`, so time decay does the work — yesterday's reports do not hold an all-clear hostage). Raising a severity, and reversing another official's clearance, stay single-official and immediate. `FeatureSummary` gained `officialSeverity`, `pendingSecondOfficial` and `contradictingCount` so the UI can show a ruling that is posted but *not yet in force* — hiding a pending clearance would mislead as badly as applying it. Walked end to end on device: one official cleared (blue S0) → a second resident's S3 relayed in → gate held (back to S3, amber "naghihintay ng pangalawang opisyal") → second official agreed → released.

**A real reducer bug this surfaced — do not reintroduce it.** `resolveCrowd` was being passed the official's own event, so a lone official clearance rendered as **SX**: the official arguing with the residents at role weight 5. The crowd fold now excludes `authorRole == "official"`. Before day 10 this was invisible, because `resolveCrowd` was only ever called when no official event existed.

**Two more artboard claims changed for the same reason as day 9's.** OfficialVerify's footer says an official status is "nilalagdaan sa phone" — signed. Ground rule 4 means nothing is signed, so the copy says what actually travels (the official's name and role), and `official/OfficialSubmit.kt` records the gap plainly: **a device on this mesh cannot tell a genuine kagawad from anyone who flipped the role switch.** Roles were a labelled demo toggle on day 10. The 6 September rebuild below makes the *flow* real — apply, activate, revoke, all replicated — but not the *trust*: without signatures a claim is still an assertion under a name, and that sentence in `OfficialSubmit.kt` stands unchanged.

**What the evacuation screen deliberately omits, and why:** "Ituro ang daan" and "1 baha sa ruta" are day 11's route check; the facility chips (Kuryente / Tubig / PWD access) are not in `assets/evacuation_centres.json` and nobody has validated that list for a real barangay. Capacity *is* shown but labelled "(tantiya)" with the full caveat in the footer, because the fixture's own `capacityEstimateSource` reads "PLACEHOLDER — not verified". Centres default to "Hindi pa bukas" until an official opens one: a centre in the fixture is a building that *could* be opened, and showing it as accepting would send people to a locked school.

**A full-system test pass on 6 September re-verified build days 1-5, 8 and 10 end to end on `API34_Test` and turned up six things worth fixing — none of them a crash, and none of them a regression in the day gates themselves.** The build was green (56/56 unit tests, lint clean, debug + release) and every airplane-mode DoD held in front of the camera: cold relaunch renders the real demo area from the 8-tile pack, confirming a seeded S3 moved Unverified → Likely live, a report filed 33 m from the saved home fired `flood_critical` with zero network, the rescue card raised itself at the 30 s threshold and its QR decoded out of the **raw uncropped screenshot**, and Bluetooth off → on restored the mesh in about a second with no restart. Zero crashes in logcat across the whole session. What follows is what was actually wrong.

1. **`location/LocationFetcher.kt` has no timeout, and the SOS button gives no feedback while it waits.** `getCurrentLocation(PRIORITY_HIGH_ACCURACY)` is wrapped in a `suspendCancellableCoroutine` with no deadline: if neither the success nor the failure listener ever fires, the coroutine suspends forever and the caller simply never resumes. Observed live — with a stale fix the red SOS button did *nothing*, twice, no spinner and no label change; injecting a fresh fix opened the hold screen instantly. Four call sites await it (`MapScreen` ×2, `ConfirmDisputeSubmit`, `KaAlertoApp`), and only "Mag-ulat" shows a pending state ("Kinukuha ang lokasyon…"). Play Services does eventually give up on real hardware, but the app has no deadline of its own, and `MapScreen`'s own doc comment promises a "GPS-first, **last-known-fallback**" that the code never implements — `lastLocation` is never read. This is the product's headline claim failing silently on exactly the phone least likely to hold a lock: indoors, under a roof, in a storm. Fix the fetcher (a `withTimeout` plus a real last-known fallback), not the four call sites.

2. **Two seed reports carry developer annotations in the user-visible `note` field.** Reports 17 and 18 in `assets/seed_data.json` read "CONFLICT PAIR (a)…" and "CONFLICT PAIR (b): Deliberately contradicts the report above — 'safe now' at the same spot, 2 minutes later. **This pair must render as SX.**" `note` is what the detail sheet renders as the resident's own words, and this is the exact feature the flagship SX demo opens — so a judge tapping that marker reads the test plan. Move the annotations to a sibling key the loader ignores.

3. **A first run with no network shows an indefinite "Downloading offline map · 0%".** The banner replaces the entire map header (taking the role badge with it) and the map draws nothing at all — no tiles *and* no markers, because the marker layers live inside a style that itself never loads, so `onStyleLoaded` never fires. The 19 seeds were in Room the whole time. The download does resume correctly the instant connectivity returns (confirmed: "Pack complete: 8 tiles"), so the pipeline is sound and this is a copy problem — but an unbounded 0% progress bar is precisely the spinner `docs/03-architecture.md` §6.4.4 forbids, and the app already knows connectivity is false (`Mbgl-ConnectivityReceiver: connected - false`). Say so instead. Worth caring about because the PRD deliberately designs for someone installing mid-flood.

4. **`UNREACHABLE` asserts a broadcast that is not happening.** The banner reads "Sinusubukan pa rin. Patuloy ang pag-broadcast ng phone mo." while the three channel rows directly beneath it say mesh is unavailable and SMS/server are unbuilt. Never wording `UNREACHABLE` as a failure is the right instinct and should stay; claiming an activity that no channel is performing is the same category of false progress the rest of the build is careful about. When zero channels are live the honest line is nearer "Walang maabot ngayon — awtomatikong susubok ulit".

5. **Storm mode leaves the map surface bright.** Chrome, legend, filter bar and action bar all invert correctly; the MapLibre style does not, so roughly 60% of the screen stays a white rectangle in the one mode built for a dark night and a dying battery. Needs a dark style (or a raster invert) on the same toggle.

6. **The evacuation entry is a house glyph in the bottom-right corner of the map** — the position and icon every map on earth uses for "my home / recentre", in an app that *has* a home-location feature two gestures away. The floating-button decision from day 10 is right; the glyph is not.

Smaller, all confirmed on device: `SosContext.PEOPLE_OPTIONS` stores the display label verbatim, so `"2–4"` puts a U+2013 en dash in the QR payload and on the wire — it is not in the GSM 7-bit alphabet at all, which will bite day 12's 160-character packing; "Bukong-buk/ong" wraps mid-word in the report screen's first depth chip, which is the default selection and therefore always on screen; the KAGAWAD chip overlaps its own description row on `RoleScreen`; and answering the five SOS context questions writes five `sos_amend` events each carrying the *whole* cumulative context rather than a delta — sending each answer as it is tapped is deliberate and documented on the screen itself, but the payload need not grow each time.

**Roles were rebuilt as an event-sourced activation flow on 6 September — built, tested and verified, but currently PARKED behind `identity/RoleMode.EVENT_SOURCED = false`.** The app today still shows day 10's self-select toggle (`ManualRoleScreen`), because the real flow is one-way by design — claiming a seat has no un-claim, and `role_revoke` stands down a responder rather than a seat holder — which is right for a barangay and wrong for a bench, where one device has to walk all three roles in a sitting. Flip the flag to switch the whole app over; the screens, the guards and the role cache all key off it and nothing else needs editing. **Delete `ManualRoleScreen` and `LocalIdentity.setRoleForTesting` when it flips** — a role you can grant yourself is exactly what the decision table forbids, and leaving it reachable in a shipped build would undo the rebuild no matter what the fold says. What the rebuild does, when live: `identity/` gained `BarangayRoster.kt`, `RoleEvents.kt`, `RoleReducer.kt` and `RoleViewModel.kt`; `RoleScreen.kt` is no longer a list you pick yourself from. A resident *applies* (`role_request`); an official *activates* them (`role_grant`, or `role_revoke` to stand them down); and the official role itself comes only from claiming a seat in `assets/barangay_roster.json` (`role_claim`). All four are ordinary events on the same append-only log, so they replicate over the day 6-7 mesh with no new transport code, and `foldRoles` derives every device's role from them. **`LocalIdentity` no longer has a setter any screen can call** — what it stores is a cache of the fold, refreshed by `RoleViewModel`, kept only because the guardrail says a name and role are embedded in an event at creation and the write path may not wait on a re-fold. 18 new unit tests; 74/74 and lint pass.

**Four things about the role fold that are easy to undo:**

1. **It resolves in three passes, never one forward walk.** The mesh delivers in whatever order the radio managed, and NFR-4 says two devices holding the same events must agree — so a grant arriving *before* the claim that authorised its author must still count. Folding forward once would silently drop it. Every ordering decision is an explicit sort on `(timestampMs, id)`; a test rotates the whole event list and asserts all rotations fold identically.
2. **An official cannot grant official.** Authority enters at a roster seat and never spreads sideways, so one mistaken or malicious holder cannot mint more. A `role_grant` naming `official` is ignored by the fold *and* refused at the point of writing — the fold protects the barangay, the write guard protects the user from being the only device that thinks something worked.
3. **The roster is seats, not people, and that is deliberate.** RA 7160 §387 fixes the composition (one Punong Barangay, seven Kagawad, one SK Chairperson) so the offices are real; who holds them in San Juan Bautista has never been collected, and putting invented names against real elected offices would be fabricating a public record. A device supplies the person by claiming.
4. **Role events carry a one-year TTL and `lat`/`lon` of 0.0.** Every other event is an observation that goes stale and gets purged; "the barangay activated Boy" is not, and expiring it would demote every responder mid-flood on whichever phone purged first. Revocation is an event, not a timeout. The null island coordinates are on purpose too — who holds a seat is not a fact about where they stood when they claimed it.

**What this still is not, and the screen says so.** Ground rule 4 forbids crypto, so nothing is signed and a forged `role_grant` remains indistinguishable from a real one. What the rebuild buys is that a claim is *attributable* (name and time), *replicated* (every phone folds the same answer) and *contestable* (two devices claiming one seat renders red as "pinagtatalunan", earliest claim holding, same stance as the map's SX). The banner changed from "this switch is a demo" to saying that — removing the caveat entirely would be the same misrepresentation in the other direction. Verified on `API34_Test` in airplane mode: claimed a seat → KAGAWAD with provenance; a peer's `role_request` injected as the mesh would have stored it appeared in the activation queue → granted → `role_grant` written ready to relay back; then an *earlier* rival claim on the same seat was injected and **the device demoted itself to Residente**, voiding the grant it had authored. A stale self-granted `role = official` left in prefs by the old build is likewise demoted on the first fold.

**Both non-resident roles had no way in, found by testing the role switch by hand (6 Sep) — do not remove `map/RoleActionStrip.kt`.** Switching to Responder or Barangay Official changed nothing anyone could see, which read as "those features are not built". They were built; they were unreachable. **The rescue queue had no inbound link at all** except tapping an incoming SOS alert — and that alert only exists when somebody else has an open request — so a responder on a quiet day could not open the one screen their role exists for. The official's ruling screen and the evacuation controls *were* reachable, but only by tapping a marker or the shelter icon with nothing anywhere saying so. This is `docs/05-routing-matrix.md` §8's "role landing screens with no inbound links", now closed for these two.

The strip renders under the map header for any role above resident: a rescue-queue entry carrying a live count of other people's open requests, plus one line for an official pointing at the marker and shelter affordances rather than adding two more buttons to a header day 10 already had to unload. **The queue entry is shown even when the queue is empty, saying so** — a control that appears only when it has contents teaches a responder that its absence means "nothing is being asked of me", when it actually means "nothing has arrived on this phone yet", and those are different claims on a mesh where one of them cannot be made.

**A stale-identity bug the same pass surfaced.** `SosViewModel` captured `LocalIdentity.getOrCreate()` once at construction, and day 9's `setRole` used to refresh it by hand. The role rebuild moved role ownership to `RoleViewModel` and removed that setter, so the cached copy went stale: a responder who had just switched role would author an acknowledgement stamped `authorRole = "resident"`, under their old display name. It is a computed property now, re-read on every use. `authorId` never changes, so the flows are unaffected — it is the role and the name that move.

**`QueueOfficial` was built on 6 September as the official tier of the existing rescue queue, not a second screen** (`sos/SosTriage.kt`, `SosQueueScreen` gained `isOfficial`). One screen with two tiers is the honest shape here, because the artboard's headline official-only feature — medical context — **cannot be built at all**: `redactForMesh` strips medical needs irreversibly at the first hop (day 9), so a relayed request carries none for any screen to show. An official sees medical only for a request raised on their own phone, which is nearly never. The two features that *are* buildable both shipped, with 13 new tests (87/87 and lint pass):

1. **Same-incident grouping.** Open requests within `SAME_INCIDENT_RADIUS_M` (40 m) collapse to one row. **The artboard says "iisang bahay" and this code deliberately does not** — fixes seen on this build ran ±5 m to ±100 m, and at ±100 m a cluster is a block, not a building. So the row states the radius, prints the worst accuracy in the group ("maaari itong magkahiwalay na bahay"), and lists every grouped request underneath. Grouping is a reading aid, never a filter: a queue that hid a live request because something nearby looked like it would be the worst bug this screen could have, and a test asserts no request is ever lost. Clustering is seed-and-radius, not transitive — chaining would let requests 39 m apart merge a whole street.
2. **False-alarm marking**, the artboard's "Markahan: walang emergency" — attributed by name, and **undoable by any official**, because a wrong call about an emergency must not be permanent.

**The demotion is bounded to sort order, and that boundary is the point.** The artboard says "bababa ang pagkakasunod ng susunod na request ng device na ito" — *ranks lower*, not disappears — and that is exactly what `officialQueue` implements. A marked request and a request from a previously-marked device both sink; neither is filtered, hidden or collapsed, both keep their acknowledgement buttons, and the row says why it sank. This is the **only** mechanism in the app that can make a live emergency quieter — every other one errs loud, which is what the reducer's whole safety asymmetry is built on — so it is deliberately fenced. Do not extend it into hiding, auto-closing or suppressing an alert.

**Two things about it that are honestly weak, and are not bugs to fix in code.** The official cannot see *who* they are marking on a relayed request: `redactForMesh` replaces the name with "Hindi ipinapakita" and leaves only `authorId`, so the mark keys on an anonymous device. And with no signatures a forged mark is indistinguishable from a real one, so anyone on the mesh could demote a neighbour's future requests. `foldTriage` ignores marks whose author is not an official and `SosViewModel.markFalseAlarm` refuses to author one, which is the most that can be done without crypto. Both belong in the pitch's honest-limitations list, not in a patch.

**`SosSnapshot` gained `authorId`** for this — it survives mesh redaction where `authorName` does not, and it is what the false-alarm history keys on.

**Two hard-won toolchain findings, both now fixed in code — do not re-derive or revert them:**

1. **`demotiles.maplibre.org` is unusable for offline packing — permanent, do not switch back.** It hits [maplibre-native#4403](https://github.com/maplibre/maplibre-native/issues/4403), a currently-open upstream bug: MapLibre's offline resource-matching code fails to escape `{fontstack}`/`{range}` placeholders in a glyphs URL before compiling it as a regex, and this specific domain hits exactly that broken path — `std::regex_error: invalid range in a {} expression`, an uncaught native exception that `SIGABRT`s the whole process. Confirmed: merely calling `OfflineManager.listOfflineRegions()` while that style is active crashes, no `createOfflineRegion()` needed. `DemoArea.STYLE_URL` is OpenFreeMap now — the maintainers' own named workaround.
2. **The default `org.maplibre.gl:android-sdk` artifact is Vulkan-only** — confirmed via a runtime `UnsupportedOperationException` when explicitly requesting OpenGL. The dependency is `android-sdk-opengl` (see `gradle/libs.versions.toml`) instead. This was chased down while root-causing a much bigger, separate problem below and turned out not to be that problem's actual cause — but it's a correct, deliberate choice on its own merits: OpenGL ES has far more universal support across the low/mid-tier Android device landscape this project targets (PRD §86), so it stays even though it wasn't the fix.

**The actual root cause of "map renders solid black, no crash, no error" was the dev machine's original emulator, not our code.** That AVD (`Medium_Phone`, still present) runs **Android 17.0 "CinnamonBun" / API 37.0** — a very new system image released around the same time as MapLibre 13.6.0 itself. Isolated by elimination: not the crash fix (already applied), not Vulkan-vs-OpenGL (both backends painted solid black identically), not our style or tiles (a trivial zero-dependency red-background style *also* painted black, even though its style-loaded callback fired) — the `SurfaceView` attached correctly and MapLibre self-reported a loaded map in the view hierarchy, but the native compositor never produced a visible frame, on either backend, on that OS image. A second AVD, **`API34_Test`** (Android 14.0 "UpsideDownCake" — a mature, extremely widely-deployed release), was built specifically to test this and renders everything correctly. **Use `API34_Test` for map work until this is confirmed on real hardware or a newer MapLibre release fixes API-37 compatibility.** `sdkmanager`/`avdmanager` are now installed at `android/../cmdline-tools` (well, `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`) if another AVD is ever needed — they weren't present before.

**The Android project itself is scaffolded and verified building.** `./gradlew build` completes green — debug + release APKs, lint clean, unit tests pass. Every dependency needed through build day 13 is wired and resolving (MapLibre, Room + KSP, Nearby Connections, FusedLocation, serialization), so no build day is blocked on dependency setup.

**The Node server is further along than Day 0 called for** — both day-13 endpoints exist and are tested, not just scaffolded: `POST /events/batch` (idempotent on event ID) and `GET /events?bbox=&since=&limit=` (bbox-scoped, cursor-paginated), plus `GET /health`. 9/9 tests pass, and a live process was smoke-tested end-to-end against the real seed fixture — posted, pulled back with the conflict pair intact, confirmed idempotent on re-post, confirmed to persist across a restart. No signature/role verification (matches ground rule 4 — no crypto, no auth for this build). The `/events/batch` cursor is a single global value, not per-device-region as `docs/03-architecture.md` §392 describes — an intentional simplification for one demo region, noted in the code. `dashboard/` is still an empty placeholder.

`BUILD_TASKS.md` (repo root) is the day-by-day implementation list, stripped of the submission ceremony in `docs/04-build-plan.md`. `SETUP_CHECKLIST.md` tracks the remaining non-code prep — devices, SIMs, and the OSM extract.

**Where the schedule actually stands:** `docs/04-build-plan.md` front-loads build days 1–2 into week 1, specifically so the offline-tiles gate gets attempted on ~2 September rather than 15 September. Raw arithmetic is 15 build days into a 15-day window with zero buffer, and front-loading is what creates the only slack there is.

**The single highest-risk task is offline map tiles** — MapLibre `OfflineManager` pre-download, verified in airplane mode, with bundled MBTiles as the bulletproof fallback. That verification has not happened yet; nothing about the map is proven until it has run on real hardware in airplane mode.

---

## Decisions already made — do not re-litigate

These were settled deliberately. Reopen only if the user asks.

| Decision | Why |
|---|---|
| **Node + Express + `node:sqlite`** for the server | Supabase was evaluated and rejected. A hosted service can pause, rate-limit or need a round trip, in a product whose claim is that it needs none. Node 24 ships SQLite in stdlib, so `better-sqlite3` (native module, Windows build pain) is not needed. **Express is the only npm dependency.** |
| **Roles are event-sourced, and official comes only from a roster seat** | Rebuilt 6 Sep, **parked behind `RoleMode.EVENT_SOURCED = false`** for manual testing. A volunteer applies, an official activates; nobody picks their own role. Without crypto this is a procedure, not a guarantee — the screen says so. See `identity/RoleReducer.kt`. |
| **Residents register a name + home barangay**, required, at first run | It is *self-declared identification used for attribution* — never call it authentication or verification. Nothing is checked. It exists so a false report has a social cost. |
| **The name is visible to everyone**, not just responders | User's explicit final choice, made after the RA 10173 exposure was flagged. Display form is first name + last initial with barangay — never a full name or doorstep. |
| A name may **never** raise a report's confidence | If it could, typing one would be a free way to raise confidence. Corroboration weight comes from relay attestation alone. |
| **SOS is reachable from the registration screen** | So someone installing mid-flood is not blocked by a form. |
| **Additional features 6–9 are gated**, not scheduled | They start only when all five core features pass on real hardware in airplane mode, including the demo script running clean start to finish. See PRD §7.6. |
| **No forecasting, ever** | Permanent scope boundary, not a hackathon deferral. Contribution to early warning is distribution, not prediction. |
| **`docs/02-prd.md` (10 pages) is canonical** | The older 234-requirement, 40-page PRD is superseded. It is not in this repo. |

---

## Architecture in one paragraph

Immutable append-only event store → deterministic reducer → displayed state. Every device holds its own store and computes its own map; **the local DB is a replica, not a cache**. Two devices with the same events must display the same status (NFR-4) — this is the invariant that makes an offline phone trustworthy rather than merely stale. Events reach other devices by three transports offered in order: server sync, SMS (bit-packed, 160 chars), and Bluetooth/Wi-Fi Direct relay via Nearby Connections. Deduplication is by content hash, so re-delivery over multiple transports is harmless. Notifications are evaluated locally on every event insert, so they fire with no push server. Full detail in `docs/03-architecture.md`.

**Three rules that are easy to break by accident:**

1. **Photos never travel by SMS or relay.** The event carries the photo's *hash*; the image queues separately at lowest priority. Otherwise devices compute different confidence depending on whether the image arrived.
2. **The author's display name is embedded in the event at creation**, like the photo hash — so a receiving device renders it offline with no lookup.
3. **Nothing on the render path may await the network.** Every read is local.

---

## Layout

```
docs/          NOT IN GIT — deliberately gitignored. Local working copy only.
               01-ideation · 02-prd (canonical) · 03-architecture · 04-build-plan · 05-routing-matrix
               exports/  .docx deliverables, submitted by file upload at each gate
design/        artboards/ (29 .dc.html) · canvas.json · screenshots/ · README.md (screen index)
               To render artboards standalone for screenshots: see "Working with design"
android/       Gradle project — open THIS folder in Android Studio, not the repo root
server/        Express + node:sqlite, ~150 lines, two endpoints
dashboard/     one HTML page + MapLibre GL JS. Not a React app
tools/         render-artboards.js · final-prd.js · ideation.js · check.py · osm-extract/ (day-0 OSM extract fetch + README)
submissions/   one file per gate; doubles as release notes. README has the gate checklist
               Macci-PRD.md (added 5 Sep) is the living, editable twin of docs/02-prd.md —
               unlike docs/, this one IS in git. Update policy: don't edit its content
               unilaterally; surface the proposed change and get an explicit decision first,
               then update it and docs/02-prd.md together so they don't drift.
```

**`docs/` is gitignored on purpose** (`.gitignore` lines 39–40). The user's decision: submission documents go up through each stage's file-upload field rather than living in the repository. Two consequences to hold onto:

- **Never link from `README.md` or `design/README.md` into `docs/`** — those links 404 on github.com. Both files have been cleaned of such links; don't reintroduce them.

---

## Document consistency — read before trusting a doc

`docs/02-prd.md` is canonical. **The build plan and architecture doc were reconciled against it on 3 September 2026.** What follows is what was changed and what is knowingly left alone.

### Reconciled — now correct

**`docs/04-build-plan.md`** — six fixes:

- All three Supabase references removed. The stack is **Node + Express + `node:sqlite`**, and the §5.1 build note now records that a hosted backend was *evaluated and rejected* rather than leaving it as a tempting option. The day-13 fallback is to **cut the server and dashboard** (where they already sit on the MVP cut ladder), not to swap architecture under time pressure.
- Ground rule 4 was "no crypto, no auth, **no accounts**" — now states the name + home barangay registration and points at PRD §9.
- The stack table row now names `node:sqlite` and records that Express is the only npm dependency.
- "hi-fi screen list (~15 — cap it here)" carries a supersession note: 29 artboards exist.

Everything else in that document — calendar, cut ladders, demo script, risk register — was already current.

**`docs/03-architecture.md`** — two fixes:

- §1.6.10 described the volunteer role as **opt-in**. It now carries a supersession note: the volunteer applies, the barangay activates, and the applicant acts as a resident until then. The capability fields were always right; only the granting mechanism was wrong.
- The reporter-metadata list (§1.5) now includes relay attestation and the reporter's display name, with the note that the name is self-declared, never checked, and excluded from confidence.

The remaining ~1,000 lines are current. Where it goes deeper than the PRD — wire formats, mesh topology, schema — it is the better reference.

### Knowingly left alone

**`docs/05-routing-matrix.md`** — still says "28 existing artboards" (there are 29) and still routes `Onboarding` straight to the map, which no longer matches the registration screen in PRD §9. Two-line fix, not yet done.

**`docs/01-ideation.md`** — five features, no roles, no tiering, no registration. **Leave it.** It is the Stage 1 artifact, and a dated snapshot is better than a deliverable retroactively edited to match later decisions.

**Rule of thumb:** when a doc disagrees with `docs/02-prd.md`, the PRD wins. Say so rather than quietly following the older text.

---

## Working with design

**Viewing artboards:**
- Live canvas (all 29 screens on one pan-and-zoom): [design canvas artifact](https://claude.ai/code/artifact/f1ee7d2c-1462-4788-bb92-5ed9b289f84a)
- Individual artboards: `design/artboards/*.dc.html` (renders inside the canvas host; see `design/README.md` for screen index)

**Rendering artboards to standalone HTML** (for headless screenshotting):
```bash
node tools/render-artboards.js design/artboards <output-dir> Map-Storm Report-Normal SOSStatus
```

Then screenshot with headless Chrome:
```bash
chrome --headless=new --hide-scrollbars --force-device-scale-factor=2 \
  --window-size=360,800 --screenshot=out.png file:///path/to/Map-Storm.html
```

---

## Tools

**Render artboards to standalone HTML:**
```bash
node tools/render-artboards.js <artboard-dir> <output-dir> <artboard-names...>
```

**Generate PRD document:**
```bash
node tools/final-prd.js <output.docx>
```
Output builds to scratchpad first, then use `cp -f` to place it (Word locks `.docx` files during writes).

**Check document compliance:**
```bash
PYTHONIOENCODING=utf-8 python tools/check.py
```
(Note: Unicode crashes on cp1252 console without the encoding prefix.)

---

## Android toolchain — settled 3 Sep 2026, do not re-derive

Established empirically by building. Full rationale in `android/README.md`.

- **`android/` is the Gradle root**, with a single `:app` module. There is no Gradle build at the repo root. Open `android/` in Android Studio.
- **The versions are a matched set** in `android/gradle/libs.versions.toml`: Gradle 9.7.1, AGP 9.3.2, Kotlin 2.4.10, KSP 2.3.11, compileSdk/targetSdk 37, minSdk 26. Bump them together.
- **AGP has a second ceiling beyond what Gradle allows: what Android Studio's own sync accepts.** The Gradle CLI happily built AGP 9.4.0, but Android Studio 2026.1.3's sync hard-refused it (`android.studio.latest.known.compatible.agp.version=9.3.0`, found in `idea.log`). A green `./gradlew build` does not mean Studio can open the project — check Studio's actual sync log if this is ever bumped.
- **AGP 8.x can never work here.** It calls `InternalProblems`, a Gradle internal API removed in Gradle 9.6 — and Gradle 9.x is required because Studio's JBR is JDK 25. This is a hard constraint, not a preference.
- **AGP 9 has built-in Kotlin support.** Applying `org.jetbrains.kotlin.android` is a build error. Only the Compose and serialization Kotlin plugins are applied.
- **compileSdk is 37 because that is the only platform installed**, and there is no `cmdline-tools`, so `sdkmanager` cannot fetch another. Changing it forces a download.
- **Room uses KSP, not kapt.** KSP renumbered at 2.3.0 — it is plain semver now, no longer `<kotlin>-<ksp>`.
- Gradle 9.7.1 lives at `C:\Users\pol\.gradle-dist\` and is seeded into the wrapper cache, so `./gradlew` does not re-download it.

**Three fixes that lint or AAPT will fight you about:**

1. **The adaptive icon must stay in `mipmap-anydpi-v26`.** Lint's `ObsoleteSdkInt` says merge it into `mipmap-anydpi` since minSdk is 26; doing so makes AAPT2 fail with "resource mipmap/ic_launcher not found". Suppressed narrowly in `app/lint.xml`.
2. **Every `uses-feature` is `required="false"`.** A required feature would contradict the product claim and block installs on devices without telephony.
3. **The SMS receiver is guarded by `BROADCAST_SMS`**, not `RECEIVE_SMS`. Without it anyone can spoof an `SMS_RECEIVED` intent and inject a fake report or SOS.

---

## Environment gotchas

- **Windows + Git Bash.** Heredocs through the Bash tool fail unpredictably on this setup — use the Write tool for any file with quotes or non-ASCII.
- **No `java` or `gradle` on PATH.** Use Studio's JBR: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`, then `./gradlew` from `android/`.
- **Word locks `.docx` files.** Build to a scratchpad path first, then `cp -f` into place. If it fails with `EBUSY`, the file is open in Word.
- **`docx` npm package resolves from `C:\Users\pol\node_modules`** — walking up from the script directory. Generators run as `node tools/final-prd.js <output.docx>`.
- **`tools/check.py` crashes on Unicode output** in a cp1252 console. Prefix with `PYTHONIOENCODING=utf-8`. It also flags timelines and "V0"/"MVP" as prohibited — those rules were written for the old PRD and do not apply here.
- **Artboards need the canvas host to render.** `.dc.html` files use `{{bindings}}`, `sc-for` and a `support.js` that is not in the repo. Use `tools/render-artboards.js` to produce standalone HTML, then headless Chrome for screenshots. Method documented in `design/README.md`.
- Artboards are sandboxed and **cannot route to each other**. `dc-import` is the only mechanism and it **breaks on hyphenated filenames**, killing the whole render.

---

## Known open items

- **The published design canvas is behind `design/artboards/`.** `Onboarding` was redrawn on 3 September (name + barangay fields, GPS-filled barangay, SOS control, name-visibility disclosure) and measures 800/800 with no clipping. The canvas Artifact still shows the old version until re-seeded and republished — do that before Stage 2, since the README and `design/README.md` both link to it.
- The canvas URL in `README.md` and `design/README.md` is unverified — confirm before the repo goes public.
- `LICENSE` says "Team MACCI"; no individual name.
- **The repo must be public before 7 September** or every submission link 404s.
- ~~Rescue-card QR is a drawn placeholder, not scannable.~~ **Closed for the app on build day 8** and re-verified 6 September by decoding it out of a raw device screenshot. The *artboard*'s QR is still decorative — that is a `design/` item, not an app one.
- All Filipino copy is unreviewed by a native speaker. Separately from that review, the 6 September pass found English strings sitting inside Filipino screens where nothing bilingual was intended: `Impassable` in the filter bar, `Unverified`/`Likely` in the detail sheet, `Seed data`, `18 min ago`, `Evacuation centre` (also singular over a list of four), and a confirmation rendering as "Kumpirmasyon · Residente 89A7 · **Resident**" — the role appended in English onto a name that already carries it.
- **`Dashboard` (LGU web console) and `VolunteerRegister` are the official-side artboards still unbuilt.** `dashboard/` is an empty placeholder scheduled for day 13; `VolunteerRegister` exists only inside the parked event-sourced role flow. `QueueOfficial` is built except for its medical column, which day 9's redaction makes impossible rather than pending.
- `docs/05-routing-matrix.md` §8 lists eight routing gaps. The two role landing screens are closed as of 6 Sep (`map/RoleActionStrip.kt`); no cleared-detail screen and no post-submit confirmation remain.
- No field measurement of relay range, delivery rate or battery cost. Named honestly in the pitch rather than implied to be done.
- **The event-sourced role flow is parked** behind `identity/RoleMode.EVENT_SOURCED = false` while roles are being tested by hand; the app ships day 10's self-select toggle until it flips. It also has **no seat-release path** — a claim is one-way, which is what made the flag necessary. Decide whether releasing a seat should exist before going live.
- **The day-4 reducer has no test file of its own.** Rules A-D, Wilson scoring, time decay and SX detection are exercised only incidentally, through day 10's `data/OfficialOverrideTest.kt`. For the piece this project calls its intellectual core that is the thinnest coverage in the build.

---

## Build sequence and gate structure

The project follows a 15-day build schedule divided into five gates (one per September week). Each gate is equally weighted in scoring.

**Pre-build (now):** Offline map tiles is the single highest-risk task. MapLibre `OfflineManager` pre-download must be verified in airplane mode before proceeding to other features. Bundled MBTiles is the bulletproof fallback.

**Feature gates (days 3–15):** Features are gated behind airplane-mode testing on real hardware. See `docs/04-build-plan.md` for the full schedule, cut ladders, and the demo script that must run clean from start to finish.

**What constitutes "built":** A feature is done when it passes on real hardware in airplane mode, with seed data, and works in the demo script. The demo script is canonical (`docs/04-build-plan.md` §11) — if it's not in the script, it's optional.

---

## Working style

- **Every day ends with something demonstrable.** A day ending in "the refactor is halfway done" was a lost day.
- **Test in airplane mode every single day.** The one claim that cannot break on stage is the one the project is named for.
- **Seed data from build day 2.** An empty map demos terribly and debugs worse.
- **Demo-path first.** If it is not in the demo script (`docs/04-build-plan.md` §11), it is optional.
- Be honest in the README and the release notes about what is not built. Judges reward it and punish the alternative.

**Archive:** earlier drafts, the superseded 40-page PRD, and the hackathon-template PRD are in
`C:\Users\pol\Documents\Team-MACCI\Climate-Resilience-and-Hydrometeorological-Disaster-Management\temp-repo`.
