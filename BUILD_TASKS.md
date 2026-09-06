# KaAlerto — Implementation Tasks

**Solo developer · 15 build days · Android (Kotlin + Compose) + Node server + web dashboard**

Focus: code and feature work only. Stage submissions are handled separately in the build plan.

---

## Day 0 — Setup & Fixtures

### Android project setup
- [x] Gradle project initialized with Compose, min SDK 26, **target/compileSdk 37** (34 is not installed on the dev machine and there is no `cmdline-tools` to fetch it — see `android/README.md`)
- [x] Dependencies: MapLibre Android SDK, Room, Jetpack Compose, Nearby Connections, FusedLocation
- [x] Git repo initialized, .gitignore in place
- [ ] Three physical devices in developer mode, SIMs with SMS load

### Fixtures & data
- [x] **Demo area frozen: Barangay San Juan Bautista, San Nicolas, Ilocos Norte** (3 Sep 2026) — see `DemoArea.kt` for sourcing
- [x] Seed fixture JSON: 19 reports across S0–S3, real street coordinates, one deliberately conflicting pair → `android/app/src/main/assets/seed_data.json`
- [x] Evacuation centres JSON: 4 real, OSM-confirmed facilities, capacity figures marked as unverified placeholders → `android/app/src/main/assets/evacuation_centres.json`
- [x] Route GeoJSON lines: 3 real street centrelines → `android/app/src/main/assets/routes/`
- [x] **OSM map extract for offline tile building — done 5 Sep 2026.** `tools/osm-extract/demo-area.osm.pbf`, clipped to `DemoArea.bounds` (10,239 nodes, 1,787 ways, 3 relations). Fetched via the official OSM API's `/api/0.6/map` bbox endpoint instead of Geofabrik PH + `osmium`/`osmconvert`/`ogr2ogr` (none installed locally) — see `tools/osm-extract/README.md`. Source extract only; the day 1 MBTiles-fallback tile-build step itself is still not done.

### Node server skeleton
- [x] Express app scaffolded, `node:sqlite` database initialized (`server/src/db.js`, `server/src/server.js`)
- [x] Basic CORS and JSON parsing middleware — CORS hand-rolled (no `cors` package; Express is the only npm dependency per CLAUDE.md)
- [x] **Went further than day 0 needed**: both day-13 endpoints are implemented and tested, not just scaffolded — `POST /events/batch` (idempotent on event ID) and `GET /events?bbox=&since=&limit=` (bbox-scoped, cursor-paginated), plus `GET /health`. 9/9 tests pass (`node --test`), and a real server process was smoke-tested end-to-end against the actual seed fixture: 19 events posted, round-tripped via GET with the conflict pair intact, re-POST confirmed idempotent, and data confirmed to persist across a process restart.
- [ ] **Not done**: no signature/role verification (matches ground rule 4 — no crypto, no auth, this hackathon build trusts client-supplied content, documented inline in `db.js`); the `cursor` returned by `/events/batch` is a single global cursor, not per-device-region as `docs/03-architecture.md` §392 describes — an intentional simplification for the demo's one region, noted in code.

---

## Days 1–2 — Offline Map (Critical Path)

### Day 1: Map scaffold + offline tiles — DONE, verified 3-4 Sep 2026

**OfflineManager path chosen. DoD met on the `API34_Test` emulator** (Android 14, API 34 — not the machine's original `Medium_Phone` AVD, API 37; see below).

- [x] MapLibre integrated, single screen (`MapScreen.kt`)
- [x] `OfflineManager` download over `DemoArea.bounds`, tracked via `OfflineMapPack.kt`
- [x] Tiles persist to device storage — confirmed via a real process restart (8 tiles, 198KB, found on relaunch with no re-download)
- [x] Location permission handling (blue dot untested visually — permission was granted but no GPS fix was simulated on the emulator; low risk, standard `FusedLocationProvider` usage)
- **DoD confirmed by screenshot:** airplane mode on, force-stop, cold relaunch → map renders the real demo area (San Juan Bautista label, real streets, real evacuation centres) from the offline pack, zero network, no crash

**Two bugs hit and fixed along the way — both documented in `DemoArea.kt`/`gradle/libs.versions.toml` comments, don't re-break these:**
1. The style was originally `demotiles.maplibre.org` — hits an open upstream MapLibre bug ([#4403](https://github.com/maplibre/maplibre-native/issues/4403)) that `SIGABRT`s the process the moment `OfflineManager` touches that style. Switched to OpenFreeMap.
2. **The dev machine's original emulator (API 37, "CinnamonBun") cannot render MapLibre content at all** — no crash, no error, map area just paints solid black, on both Vulkan and OpenGL backends. Root-caused by elimination, not yet explained upstream. A second AVD, `API34_Test` (Android 14), was created specifically to test on a mature OS version and works perfectly. **Use `API34_Test` for all map testing** until this is confirmed on real hardware.

**Bundled MBTiles path (5h):**
- [ ] MBTiles in `assets/`, copy to app storage on first launch
- [ ] Local style JSON pointing at copied MBTiles
- [ ] Location permission and blue dot
- **DoD:** Same as above, with zero network dependency

**Decision:** If you hit 3pm and tiles aren't loading, abandon current path and switch before day ends.

### Day 2: Event model + local store — DONE, verified 5 Sep 2026

**DoD confirmed by screenshot on `API34_Test`:** airplane mode on, force-stop, cold relaunch → all 19 seeded markers render over the real demo area, colored by severity (amber S1, orange S2, red S3, blue S0), zero network. `Event.authorName` is also included (not in the original bullet list below) per the architecture guardrail that the author's display name is embedded in the event at creation, not looked up.

- [x] Room database schema:
  - `Event`: id, type, lat, lon, featureRef, severity, waterLevel, authorId, authorName, authorRole, timestampMs, expiresAt, origin, hopCount, note — `data/Event.kt`
  - `FeatureState`: featureRef, severity, confidence, bucket, isConflicted, lastReportMs — `data/FeatureState.kt`. Schema only; nothing writes to it yet, the reducer that populates it is day 4.
- [x] `EventRepository` with deduplication by content-hash ID (`data/EventRepository.kt`, `data/EventDao.kt`) — `OnConflictStrategy.IGNORE` on the id primary key, exactly the 5-line dedup story
- [x] Seed loader reading `assets/seed_data.json` on first launch only (`data/SeedLoader.kt`) — resolves the fixture's `timestampMinutesAgo`/`ttlMinutes` offsets against wall clock at load time, per the fixture's own `_meta.schemaNote`
- [x] Map markers colored by severity from seeded events (`map/EventMarkers.kt`, wired into `map/MapScreen.kt` via `map/MapViewModel.kt`) — one dot per event, not the feature-level reducer; the seeded conflicting pair (018/019, identical coordinates) renders as an S3 dot with the S0 dot fully occluded underneath, which is correct for what's built so far — the SX conflict render is day 4.
- **DoD:** App launches offline, shows 19 seeded markers (actual fixture count — the original "20" here was written before the fixture was finalized) in correct colors at correct places

---

## Days 3–5 — V0 Core (Offline Reporting)

### Day 3: Reporting flow — DONE, verified 5 Sep 2026

**DoD confirmed on `API34_Test`, airplane mode, cold relaunch:** FAB → GPS fetch → Report screen with the exact copy/layout from `design/artboards/Report-Normal.dc.html` → pick a depth → severity auto-derives and shows → submit → back to Map → new marker renders instantly at the right place in the right color, with zero network. Also exercised the map-tap fallback path (GPS denied/no-fix) end to end. Both a GPS-sourced and a map-tap-sourced report round-tripped through the real local Room DB and rendered correctly.

- [x] Water-level picker: `report/WaterLevel.kt`, `report/ReportScreen.kt`. Body scale (ankle/knee/waist/chest) copy and severity mapping lifted verbatim from `Report-Normal.dc.html`'s own embedded data, not reinvented. Vehicle scale (car/truck/motorcycle-only/nothing) added per this bullet's "+ vehicle icons" — no artboard specifies it, so its copy/mapping is grounded in `docs/03-architecture.md`'s own S1–S3 vehicle-passability text; see comments in `WaterLevel.kt`. Both scales get a dynamic Canvas-drawn illustration (`WaterLevelIllustration.kt`) rather than the design's hand-drawn SVG, which is host-specific to the design canvas.
- [x] Location input (`location/LocationFetcher.kt`, wired through `map/MapScreen.kt`'s FAB and pick-mode): one-shot `FusedLocationProviderClient.getCurrentLocation` primary, falling back to a map-tap mode (tap listener on the existing MapLibre view, not a second map) on missing permission or no fix. No snap-to-road, per the bullet.
- [x] Auto-derive severity from the selected depth, shown in a tappable banner; tapping opens a manual override dialog (`SeverityOverrideDialog` in `ReportScreen.kt`) that takes precedence until the depth selection changes again.
- [x] `report/ReportSubmit.kt` writes directly to `EventRepository` (day 2) on submit; the map's existing `Flow`-backed marker rendering picks it up with no extra plumbing.
- **Known gap, not scheduled anywhere:** no registration/onboarding screen exists yet (PRD §9), so `identity/LocalIdentity.kt` is a stand-in — a per-install generated placeholder name/id, clearly not a real collected identity. Real registration should land before this goes near an actual user.
- **DoD:** In airplane mode, file a report in <15 seconds and see it on the map

### Day 4: Confirm/dispute + reducer — DONE, verified 5 Sep 2026

**The intellectual core — give this a full day.**

**DoD confirmed on `API34_Test`, airplane mode, cold relaunch:** the seeded conflicting pair (018/019) renders as a distinct purple SX marker with a thicker stroke, and its detail sheet shows the "Magkaibang ulat" conflict card with both underlying reports listed. Confirming a normal (non-conflicting) seeded S2 report moved its bucket from Unverified to Likely live in the open sheet, with no manual refresh — the whole chain (Room insert → Flow → reducer recompute → Compose recomposition) is reactive end to end. Disputing with "Humupa na" (cleared now) correctly wrote severity S0; a single dispute did not by itself lower the seeded feature's displayed severity, matching Rule B.

- [x] Detail sheet (`detail/DetailSheet.kt`): severity/SX badge, water level + severity text, age, "PAANO DUMATING" (origin/hop count), confidence bucket with progress bar, confirm/dispute counts, and up to 5 history rows with author name + role. Layout and copy for the non-conflict and conflict states are lifted from `design/artboards/DetailConfirmed-Normal.dc.html` and `DetailConflict-Normal.dc.html` respectively, including the exact "Malapit ka ba? Tulungan mo kaming i-check." line.
- [x] Confirm action ("Tama") and dispute action ("Iba na") with the four follow-ups (`detail/ConfirmDisputeSubmit.kt`) — cleared now → S0, worse/shallower → one severity tier up/down from the currently displayed severity, wrong location → no severity (recorded, excluded from weighting). Both fetch the acting device's own GPS position first, same one-shot flow as day 3's report location, so the reducer can weight the action by real proximity.
- [x] Reducer as a pure function (`data/Reducer.kt`) — deliberately a simplified reading of `docs/03-architecture.md` §4-5, matching this bullet's own literal spec rather than the fuller certificate/sensor-tier/independence-discounting system (no crypto in this build, ground rule 4):
  - Weight = role (resident 1.0 / responder 2.5 / official 5.0) × proximity (100/500/2000 m bands from the architecture doc) × `exp(-age/τ)`, τ = the same per-severity TTL table as the seed fixtures and `report/WaterLevel.kt`.
  - **Rule A** (fast escalation) and **Rule B** (≥2 proximate observations at a lower tier, or one official event, to de-escalate) implemented as: the highest claimed severity with real weight is the ceiling, overridable downward only when a lower tier clears the day-4-literal 2-observer bar.
  - **Rule C**: both a "dangerous" (S2/S3) and a "safe" (S0/S1) reading with substantial weight, and no de-escalation bar met → severity `"SX"`. Rendered as a distinct purple marker (not literal hatching — day 2/3's markers are points, not line/polygon road segments, so a hatch pattern has nothing to apply to at this stage).
  - **Rule D**: any official-role event pins severity and bucket `Official` outright, clearing any conflict flag.
  - Confidence via the Wilson score lower bound (`docs/03-architecture.md` §5.3, z≈1.44), buckets at the doc's own 0.35/0.65 thresholds, with the "single report is always Unverified" clause enforced explicitly rather than left to floating-point luck.
  - Stale = the feature's most recent contributing event has itself decayed past its own TTL; rendered desaturated (45% opacity) on the map, per the day-4 bullet.
- [x] New reports (`report/ReportSubmit.kt`) now get a geohash-8 `featureRef` instead of `null` — day 3 correctly skipped road-snapping, but day 4's reducer needs *something* to group same-spot reports by, and a geohash cell is the fallback `docs/03-architecture.md`'s own schema names.
- **Known gap:** map-marker tap-to-select needed a rect-based, nearest-feature hit test (`map/MapScreen.kt`'s `nearestTappedFeatureRef`) rather than an exact point query — small circle markers sitting close together (like the conflict pair next to its Sotto Street neighbours) made exact-point hits unreliably miss. Not a design defect, just Android touch-target reality; documented in code.

### Day 5: Notifications + filters — DONE, verified 5 Sep 2026

**DoD confirmed on `API34_Test`, airplane mode, cold relaunch:** long-pressed the map to set a home location, adjusted and saved a 300 m radius (a real geographic polygon, `map/GeofenceCircle.kt` — not a fixed-pixel `CircleLayer` radius, which doesn't scale correctly with zoom), then filed a fresh report inside it. A local notification fired immediately — "S1 — Madaanan, mag-ingat · 33 m mula sa bahay mo" — with zero network, and the home circle itself survived the force-stop/cold-relaunch (read back from `SharedPreferences`).

- [x] Home location long-press + radius slider (`geofence/HomeLocationStore.kt`, `map/HomeRadiusOverlay.kt`, `map/GeofenceCircle.kt`) — long-press starts a draft, the slider (100-1000 m) adjusts it live with the circle redrawing on the map, "Itakda" persists it.
- [x] Geofence check on every event insert, entirely local (`geofence/GeofenceNotifier.kt`) — an application-scoped observer diffs the event table's own `Flow` (no per-insert-call-site wiring needed) and checks newly-appeared `flood_report` events against the saved home radius via `haversineMeters`. The first emission after app start establishes a baseline with no notifications, so a fresh install doesn't fire 19 seed notifications; confirm/dispute events are excluded too, matching the architecture doc's anti-fatigue rules (a confidence update isn't "new flooding").
- [x] Notification channels by severity (`notification/NotificationChannels.kt`, `FloodNotifier.kt`) — S3 uses a high-importance channel with `setBypassDnd(true)`; S0-S2 use a default-importance channel.
- [x] Filter bar (`map/FilterBar.kt`): S0-S3 severity toggle chips (conflicts always show regardless, since hiding a live disagreement is worse than a noisy map) + Lahat/1h/3h/24h recency chips, both live-filtering the map markers.
- [x] Storm Mode toggle — a manual button (moon/sun), not `isSystemInDarkTheme()`, since Storm is a condition the resident declares (docs/02-prd.md §6), not a phone setting. Switches `KaAlertoTheme`'s existing light/dark color schemes; state lives in `MainActivity`, not persisted across relaunches (not asked for, and re-toggling costs one tap).
- **Testing note, not a code gap:** `POST_NOTIFICATIONS` (API 33+) is requested alongside location permissions on first launch, but the OS presents them as two separate sequential system dialogs — worth rehearsing so a real demo device doesn't end up with the second one silently un-granted.
- **DoD:** In airplane mode, insert a report inside home radius → notification fires
- **If behind:** Keep the notification, cut the filter bar

---

## Days 6–10 — V1 (Mesh + SOS)

### Days 6–7: Nearby Connections mesh — CODE COMPLETE 5 Sep 2026, **DoD NOT MET**

**Hard gate — allocate full two days.**

Everything is written and every part that can run without a second radio has been verified on the `API34_Test` emulator. **The DoD itself has not been met and cannot be until the three phones exist** — see `SETUP_CHECKLIST.md`. Do not describe the mesh as working.

- [x] Foreground service hosting Nearby client, persistent notification — `mesh/MeshService.kt`, `foregroundServiceType="connectedDevice"`, low-importance channel with a "Ihinto" stop action so the relay is never un-stoppable
- [x] `startAdvertising()` + `startDiscovery()` on `Strategy.P2P_CLUSTER`, auto-accept — `authenticationDigits` deliberately ignored (ground rule 4)
- [x] On connect: exchange event IDs, send diff as JSON — `mesh/MeshProtocol.kt`. Anti-entropy, not broadcast: peers trade ID manifests first and send only what the other side lacks, so two reconciled devices fall silent instead of re-shipping 19 reports on every re-encounter. Batched by measured size against Nearby's 32 KB payload cap
- [x] On receive: insert with dedupe, **re-share to other peers** (genuine multi-hop) — only what was new to this device is forwarded, and never back to the sender, which is what makes the flood terminate
- [x] Peer counter in UI ("3 nearby phones connected") — third line of the map header, using the design system's own phrase ("kalapit na phone")
- [x] Mark received events with `origin: mesh`, increment `hopCount` — the content-hash `id` is left alone, so the same report still dedupes against a copy arriving later by SMS or server
- [x] Detail sheet shows "via mesh · 2 hops" — already built during the UI rebuild (`detail/DetailSheet.kt`)
- [ ] **DoD — NOT MET.** Two airplane-mode phones (report on A appears on B; phone C, out of A's range and in B's, receives via B) requires hardware this project does not yet have. The hop arithmetic that DoD checks is covered by unit test (`MeshProtocolTest`: A→B at one hop, B→C at two), which is a test of the decision, not of the radio.
- [x] **Critical debugging note** — verified as far as an emulator allows, and it paid for itself twice. (a) Nearby also needs `ACCESS_WIFI_STATE` + `CHANGE_WIFI_STATE` in the manifest; without them `startAdvertising()` fails at runtime with `MISSING_PERMISSION_CHANGE_WIFI_STATE` (8033) and nothing catches it at build or install time. (b) `startAdvertising()` reports **success even when the radios underneath it are unusable** — so `mesh/MeshRadios.kt` checks Bluetooth and location *services* directly, and `MeshService` watches `ACTION_STATE_CHANGED`/`MODE_CHANGED_ACTION` so switching Bluetooth back on after airplane mode brings the mesh up within a second with no app restart. Confirmed on the emulator in airplane mode: BT off → header reads "Buksan ang Bluetooth para sa mesh" in amber; BT on → "Naghahanap ng kalapit na phone", no relaunch.
- **If behind end of day 6:** Fall back to single-hop, drop multi-hop relay, keep moving — *not taken; multi-hop is implemented.*

### Day 8: SOS flow — DONE, DoD MET, verified 5 Sep 2026 on `API34_Test` in airplane mode

Whole path lives in `sos/`: SOSHold → SOSContext → SOSStatus → RescueCard, wired through `nav/Screen.kt`. The map's action bar finally gets the artboard's red SOS button, which was deliberately left out on day 5 because nothing was behind it.

- [x] Press-and-hold with 1.5s haptic countdown ring — `SosHoldScreen.kt`. Ring closes from 12 o'clock, three haptic ticks across the hold, one longer confirmation pulse when it lands. Uses `Vibrator` rather than Compose's `LocalHapticFeedback`, which has two semantic constants — enough to mark an event, not to pace a countdown someone is meant to feel closing.
- [x] SOS event written instantly; context screen **does not block** transmission — `SosViewModel.raise()` inserts before navigating, and every answer on the context screen is a separate `sos_amend` event appended after. Both exits from that screen ("Tapos na" / "Laktawan") go to the same place; neither is a send button, because there is nothing left to send.
- [x] State machine — `SosState.kt`. Transitions are **events**, not a column, so the current state is a monotonic fold (`mergeSosState`) and two devices holding the same events agree (NFR-4) regardless of mesh delivery order. `rank` increases strictly along every arrow in `docs/03-architecture.md` §6.2's diagram, which is what implements "a late-arriving lower state cannot regress a higher one". `EXPIRED` is deliberately absent — it is a server-side escalation decision, and a state nothing can ever set is a lie in an enum.
- [x] Honest per-channel status — `SosChannels.kt` + `SosStatusScreen.kt`. **Read the note below**: only one of the three channels exists, and the screen says so rather than showing a plausible "Sinusubukan…" over a transport with no code behind it.
- [x] Lighthouse fallback card — `RescueCardScreen.kt`. White at full brightness in every mode, 34sp monospace coordinates, people/water grid, medical strip, timestamp, QR, "Patunugin" morse SOS alarm (`SosAlarm.kt`, manual-start only per §6.4.2, since a family hiding from a hazard may need silence). It **raises itself** at `UNREACHABLE` — the card is a state, not a tap (design/README.md).
- [x] **DoD MET.** Verified on `API34_Test`, airplane mode, cold relaunch: held the button, request written at t+0, context answered, `QUEUED → BEACONING → UNREACHABLE`, rescue card raised itself at the 30 s threshold. **The QR was decoded straight out of the device screenshot** with ZXing and returned the exact payload the app wrote, including the answers entered on device and the real ±100 m GPS accuracy — so "scannable" is a checked fact, not a claim. `design/README.md`'s long-standing "rescue-card QR is a drawn placeholder" open item is closed *for the app* (the artboard's own QR is still decorative).
- **If behind:** Cut strobe and context screen; keep the QR card — *strobe cut (the artboard's "Kumurap" button is replaced by "Bumalik", since a button that flashes nothing is worse than no button); context screen kept.*

**Two channels of the three do not exist, and the status screen says so.** BUILD_TASKS' example line reads "Server: trying… · SMS: sent · Nearby phones: 2 reached". Today only the mesh row is real — it reads the live `MeshStatus` the day 6-7 service publishes. Server sync is day 13 and SMS is day 12, so those rows render dimmed as "Hindi pa gawa · wala pang code sa build na ito", with the scheduled build day named. A spinner over a transport nothing is attempting is exactly the fake success `docs/03-architecture.md` §6.4.4 forbids. **`RELAYED` is also not set even when peers are connected** — "a peer has stored it" is a claim only the peer can make, and the acknowledgement that carries it is day 9. `SosState.reachableInThisBuild` marks which states this build can actually drive, and a unit test guards it.

**New dependency: `com.google.zxing:core` 3.5.3** (the pure-Java half, deliberately not `zxing-android-embedded`, which drags in a camera scanner UI this build never needs). Pure Java means the encoder runs in unit tests, which is how the QR is decoded and asserted rather than eyeballed. The card draws the module grid on a Compose Canvas at whole-pixel module sizes — a QR scaled by a fractional factor gets soft module edges, and soft edges are what makes a code that decodes perfectly in a test fail against a real camera.

**Three bugs found on device during day 8 verification, all fixed — two of them in code the day-8 work did not write:**

1. **The rescue card's "Bumalik" was a trap.** The auto-raise was keyed on the SOS state, so returning to the status screen re-fired it on the unchanged `UNREACHABLE` state and bounced the user straight back — with no way to reach "Ligtas na ako". `KaAlertoApp` now tracks `rescueCardRaisedFor` so the card raises itself once per request.
2. **TTL cleanup was deleting stale events before they could render as stale.** `AUDIT-REPORT.md`'s fix purged at `expiresAt < now`, but the reducer derives `isStale` as `now > max(expiresAt)` — so after any cold start a stale road vanished instead of showing the grey "Luma na — kailangang tingnan" marker the legend advertises. Seen directly: 19 seed markers dropped to 16 and the dashed-clock marker disappeared. Now purges at `now - EventRepository.RETENTION_AFTER_EXPIRY_MS` (24 h); both came back.
3. **`FeatureStateDao.upsert` had been switched from `REPLACE` to `IGNORE`.** The append-only rule is about `events`; `feature_state` is the materialized fold, keyed on `featureRef` and rewritten each time the reducer runs. `IGNORE` pinned every feature to its first-seen value forever. Reverted.

**Schema change: `Event.payload` (`String?`), DB version 3.** Structured type-specific detail as JSON, for events whose content does not fit the columns — today only the `sos*` family, whose payload is a people count, medical needs and a water trend rather than a severity. It travels over the mesh as part of the event with no extra handling. `fallbackToDestructiveMigration` was already set, so upgrading wipes and reseeds.

### Day 9: SOS over mesh + acknowledgement — CODE COMPLETE 5 Sep 2026, **DoD NOT MET** (needs two phones)

**The money demo — hard gate.**

Every step was exercised on one `API34_Test` emulator in airplane mode by injecting a peer's SOS into the store exactly as `MeshService` would have written it — redacted, `origin: mesh`, `hopCount: 2`. What that cannot prove is the radio hop itself; that still needs the phones (`SETUP_CHECKLIST.md`).

- [x] SOS routes over Nearby — it is an `Event`, so days 6-7's anti-entropy exchange carries it with no new transport code. "Highest priority" is **not** implemented: the mesh sends one diff, unordered. Real P0/P1 queueing is `docs/03-architecture.md` §2.5 and is not in this build.
- [x] Receiving device raises critical alert — `sos/SosAlertNotifier.kt` + `SosAlertWatcher.kt`, own channel, alarm stream, own vibration pattern, red, `CATEGORY_ALARM`. Confirmed posted on device (`channel=sos_nearby importance=4 category=alarm color=0xffc42b2b`). **Full-screen is best-effort**: since API 34 `USE_FULL_SCREEN_INTENT` is auto-granted only to calling/alarm apps, so the code checks `canUseFullScreenIntent()` and degrades to a max-priority heads-up rather than attaching an intent it cannot use.
- [x] Responder mode toggle exposing SOS list with Acknowledge button — `sos/SosQueueScreen.kt`. The toggle lives on the nearby-SOS screen where the artboard already has "Magparehistro bilang responder", and its subtext says plainly that this is a demo stand-in for barangay activation (which is how it really works — CLAUDE.md's decision table).
- [x] Acknowledgement is an event propagating **back** over the mesh — a `sos_state` event, identical in kind to the request. Verified in the DB after tapping: `sos_state · role=responder · state=EN_ROUTE`.
- [x] Originator screen updates — §6.2's own requester-facing strings, driven by the fold: "Nakita na ng barangay responder ang hiling mo." then "Papunta na ang tulong."
- [x] Monotonic states — `SosState.rank` + `mergeSosState`, with tests for out-of-order arrival and for a stale `BEACONING` failing to un-acknowledge a claimed request.
- [x] **ACKNOWLEDGED + EN_ROUTE**, both, matching the artboard's own two buttons ("Nakita ko" / "Nakita ko — papunta na"). The queue card then shows "Papunta na si …" with the responder's real name.
- [ ] **DoD NOT MET.** Two airplane-mode phones (SOS on A → critical alert on B → acknowledge → A updates) needs hardware this project does not have. Everything either side of the radio is verified; the radio is not.
- **If behind:** Demo one-way SOS; note the ack as designed-not-built — *not taken; the ack round trip is built.*

**The privacy conflict this day forced, and how it was resolved.** `SOSNearby.dc.html` tells a plain resident "Hindi ipinapakita ang eksaktong lokasyon o kung sino sila" and "Dinadala rin ito ng phone mo papunta sa iba — hindi mo ito kayang basahin". That last line is `docs/03-architecture.md` §6.5: relaying peers hold an **encrypted** payload. Ground rule 4 forbids crypto, and the mesh relays whole `Event` rows — so medical needs and the requester's name would have sat readable on every phone in the barangay, which is exactly the RA 10173 exposure the PRD flags.

Resolved by **removing rather than encrypting** (`sos/SosMeshPolicy.kt`): what is not sent cannot be read off a relay. Medical needs and the requester's display name are stripped on the way out, irreversibly at the first hop. Coordinates, timestamp, hop data and the people count still travel — which is precisely what `QueueVolunteer.dc.html`'s footer already says a volunteer is entitled to ("lokasyon at bilang ng tao … ang detalyeng medikal ay hawak ng barangay official"), so the demo loses nothing. The *responder's* name is deliberately not stripped: the artboard's "Papunta na si Boy" is the point, and someone volunteering to walk into floodwater is entitled to be identified for it.

**The residual, stated plainly:** coordinates still travel in the clear, because a rescue needs them and there is no key to hold them under. A non-responder's screen coarsens them to a dashed circle and a distance rounded to 50 m, but that is a display choice, not a guarantee — anyone dumping a relaying phone would find the exact point. Only real §6.5 encryption fixes that. Medical-to-officials is likewise designed-not-built. The artboard's "hindi mo ito kayang basahin" line is **changed on screen** to say what is actually true, rather than claiming a guarantee this build does not provide.

### Day 10: Official role + evacuation centres — DONE, DoD MET, verified 5 Sep 2026 on `API34_Test` in airplane mode

- [x] **Superseded 6 Sep — rebuilt as an event-sourced activation flow (`identity/RoleReducer.kt`): residents apply, officials activate, official comes only from a seat in `assets/barangay_roster.json`. The toggle described below no longer exists.** Role toggle: Resident / Responder / Barangay Official — `identity/RoleScreen.kt`, reached from a role badge in the map header (the artboards put the role on screen as a KAGAWAD chip, and what you are acting as changes what your events mean to everyone else — it should not be buried in a menu). Replaces day 9's single responder switch. The banner says plainly that none of these are self-granted in the real product.
- [x] Official actions on the detail sheet — `official/OfficialStatusScreen.kt`, matching OfficialVerify/OfficialReverse.dc.html: current crowd state, the resident reports retained beneath it, three rulings, and a signing strip naming who it goes out as.
- [x] Official severity gets the **Official** bucket and a distinct badge — Rule D already produced `bucket = "official"`; the detail sheet now renders a green shield banner for it (amber when the gate is holding it).
- [x] Contradicting crowd reports stay visible with a note — verified on device: after clearing the conflict pair, the sheet read *"1 residente ang nag-uulat ng mas malala kaysa sa opisyal na status. Nananatiling nakikita."*
- [x] Evacuation centres from static JSON — `evac/`, distinct **square** pins (a shape difference, not just a hue: in a storm at night "somewhere to go" must be distinguishable from "something to avoid"), distance-sorted nearest-first off GPS, capacity shown but labelled as the unverified estimate the fixture itself flags.
- [x] Officials can change evacuation status — `evac/EvacSubmit.kt`, an ordinary `Event` with `featureRef = null` so it rides the mesh and never becomes a flood marker. Status **plus an occupancy count**, stepped in tens rather than typed: an official doing this is standing in a doorway counting people, not filling in a form.
- [x] **DoD MET.** Switched to Official, opened the seeded conflict pair, posted "Humupa na" → badge flipped to blue S0 with the green Official banner, and the contradicting resident report stayed visible.

**The DoD contradicted the artboards, and the artboards won.** OfficialReverse.dc.html states a **second-official gate** — *"Magkasalungat ang lugar na ito, kaya hindi kayang ibaba ng iisang opisyal ang severity"* — while this day's DoD has a single official clearing a conflicting road. Both are now true, because the gate is conditional: it holds only when **lowering** a severity that at least `DEESCALATION_COUNT` residents are *currently* reporting as worse. Raising a severity, and reversing another official's clearance, stay single-official and immediate. It is the same safety asymmetry the crowd path already runs on, extended to officials so one person cannot quietly overrule people standing in the water.

The full cycle was walked on device: one official cleared the pair (blue S0) → a second resident's S3 relayed in over the mesh → the gate held (**back to S3**, amber *"Opisyal na status — naghihintay ng pangalawang opisyal"*) → a second official's S0 arrived → released (blue S0, green banner).

**Two artboard claims deliberately changed, both crypto:** OfficialVerify's footer says an official status is *"nilalagdaan sa phone"* (signed on the phone), which is `docs/03-architecture.md` §2.5's signed events. Ground rule 4 means nothing is signed, so the copy now says what travels — the official's name and role — and `official/OfficialSubmit.kt` records the real gap: a device on this mesh cannot tell a genuine kagawad from anyone who flipped the role switch.

**What the evacuation screen deliberately omits:** "Ituro ang daan" (directions) and "1 baha sa ruta" are day 11's route check, and the facility chips (Kuryente / Tubig / PWD access) are not in the fixture and unvalidated for a real barangay. A button that cannot route, or an invented facility list on a screen people would walk somewhere because of, is worse than its absence.

**One reducer bug found while building this:** `resolveCrowd` was being handed the official's own event, so a lone official clearance read as a two-sided disagreement and rendered **SX** — the official arguing with the residents at role weight 5. The crowd fold now excludes officials.

---

## Days 11–15 — MVP (Full Integration)

### Day 11: Family check-in + route check

**Route check is first on the cut ladder if behind.**

- [ ] Check-in: circle pairing by QR (works offline). "Ligtas ako" emits ~20-byte event
- [ ] Circle list showing each member's status and age
- [ ] Route check: load bundled route GeoJSONs, buffer, intersect with flooded segments
- [ ] Route detail: *"2 flooded segments — 1 impassable"*, offenders highlighted
- **If behind:** Cut route check entirely (it's on the ladder); keep check-in (cheaper demo)

### Day 12: SMS fallback

- [ ] Bit-packed encoder: type (3 bits) + geohash-9 + severity (3) + timestamp-minutes (20) + people count + medical flag → Base32, one GSM-7 segment
- [ ] `SmsManager.sendTextMessage()` to configured gateway number (second phone)
- [ ] `SMS_RECEIVED` receiver parsing back to event
- [ ] UI showing "No data connection — sent by SMS" with character count visible (29 chars carrying a rescue request is visceral)
- **If behind:** Send-only. Show the received SMS in the stock app and explain the parser. 80% impact for 20% work.

### Day 13: Server + sync + dashboard

**150 lines total. If it exceeds a day, cut it.**

- [ ] Express endpoints:
  - `POST /events/batch` (idempotent on event ID, returns deduped count)
  - `GET /events?since=<cursor>&bbox=<bbox>` (cursor-based pagination)
- [ ] Sync on reconnect: flush **entire** local queue including mesh-acquired events authored by other devices (carry-forward)
- [ ] One-page dashboard: MapLibre GL JS, all events plotted, SOS list, event detail
- [ ] Dashboard read-only by default (acknowledge action is optional cut)
- [ ] Polish: Filipino strings on every user-facing label, Storm Mode audit, tap-target pass on demo path, honest offline copy everywhere (no spinners, no fake "sent")
- [ ] App icon, splash screen, final seed tuning
- **If behind:** Cut dashboard's acknowledge action (read-only is fine). Cut polling; refresh button is fine.

### Day 14: Rehearsal + backup

- [ ] Record full backup demo video **today** (not tomorrow)
- [ ] Rehearse demo at least 10× on real devices in airplane mode
- [ ] Build signed release APK, install on all three devices fresh
- [ ] Run entire demo on **release build** (not debug)

### Day 15: Demo day prep only

- [ ] **No new code.** Bug fixes on demo path only.
- [ ] Charge all devices to 100%, pack power banks
- [ ] Have backup video on laptop, ready to switch in <5 seconds
- [ ] Verify Bluetooth + Wi-Fi manually work under airplane mode on all devices

---

## Critical Path Decisions

**Do this order, non-negotiable:**

1. **Day 1: Offline map** — if it fails, the whole project fails. Front-load to 2 Sep to have two weeks of room.
2. **Day 2: Event store** — everything downstream depends on this. Seed data matters immediately.
3. **Days 3–5: Reporting + reducer** — the demo core. Rules B and C are non-negotiable.
4. **Days 6–9: Mesh + SOS** — the second core. Nearby Connections is the highest technical risk after map tiles.
5. **Days 11–13: SMS + server** — nice-to-haves but on the cut ladder; skip if time is tight.

---

## What Never Gets Cut

- Offline map working in airplane mode
- Reporting + severity rendering
- Rules B and C in the reducer (confirm/dispute logic)
- SOS reaching a second phone over mesh
- Honest offline copy (no fake "sent" states)

Cut anything else first.

---

## Per-Day Checklist

| Day | Feature | DoD | Status |
|---|---|---|---|
| 1 | Offline map | App launches offline, map renders, blue dot visible | ⬜ |
| 2 | Event store + seeding | 20 markers on map, right colors, right places | ✅ |
| 3 | Reporting flow | Report filed in <15s in airplane mode | ✅ |
| 4 | Confirm/dispute + reducer | Conflicting pair renders as SX, confirming moves bucket | ✅ |
| 5 | Notifications + filters | In-radius report triggers notification | ✅ |
| 6 | Nearby Connections | Two phones exchange events over mesh (single-hop) | ⬜ |
| 7 | Nearby — multi-hop | Three-phone relay, C receives via B | ⬜ |
| 8 | SOS + Lighthouse card | SOS → BEACONING, QR card appears | ⬜ |
| 9 | SOS mesh + ack | SOS A → alert B → acknowledge → A updates | ⬜ |
| 10 | Official role + evacuation | Official status overrides and badge appears | ⬜ |
| 11 | Check-in + route check | Both features work, route check shows flooded segments | ⬜ |
| 12 | SMS fallback | Report encoded to 29 chars, sends via SMS | ⬜ |
| 13 | Server + sync + dashboard | /events/batch and /events?since= work, dashboard shows events | ⬜ |
| 14 | Rehearsal + backup | Full demo rehearsed 10×, backup video recorded | ⬜ |
| 15 | Demo day prep | All devices charged, no new code, ready to ship | ⬜ |

---

## Demo Script (Verbatim)

See `docs/04-build-plan.md` §11 for the full script. Key beats:

1. Show flood map (phone A)
2. **Airplane mode on** (with BT/Wi-Fi manually re-enabled). Show "no service"
3. File report on A. It appears on B (no internet, no server)
4. C receives via B (out of A's range)
5. Show conflicting road (SX state)
6. **Long-press SOS on A**
7. B raises critical alert. Acknowledge.
8. A shows "Barangay responder has seen your request" (A never had signal)
9. Lighthouse card + QR on offline phone
10. SMS phone: show 29-char report
11. Turn AM off on B. Dashboard fills.
12. **Close:** "Twenty typhoons a year. The warnings stop exactly when they matter most. This one doesn't."

Rehearse 10 times before day 15.

---

## Architecture Guardrails

Three rules that are easy to break by accident:

1. **Photos never travel by SMS or relay.** Event carries photo's *hash*; image queues separately at lowest priority. Different hash arrival timing = different confidence on different devices.
2. **Author's display name is embedded in the event at creation.** Receiver renders offline with no lookup.
3. **Nothing on the render path may await the network.** Every read is local.

---

## Environment & Tools

- **Offline tiles:** MapLibre `OfflineManager` (primary) or bundled MBTiles (bulletproof fallback)
- **Local DB:** Room (skip R-Tree; `lat/lon BETWEEN` with indices is enough at this scale)
- **Mesh:** Nearby Connections `Strategy.P2P_CLUSTER` (wraps BLE + Bluetooth + Wi-Fi Direct)
- **SMS:** `SmsManager` + `SMS_RECEIVED` BroadcastReceiver
- **Serialization:** `kotlinx.serialization` → JSON (bit-packed encoder for SMS only)
- **Permissions:** Accompanist Permissions lib
- **Server:** Node 24 + Express + `node:sqlite` (~150 lines, only Express dependency)
- **Dashboard:** One HTML page + MapLibre GL JS (not React)
- **Location:** FusedLocationProviderClient

---

## If You Get Behind

**Day-by-day fallbacks (take them — every stage is worth the same XP):**

- **Day 5:** Cut filter bar, keep notifications
- **Day 6:** If Nearby won't connect by noon, abandon it; cut relay and do single-hop on day 7
- **Day 9:** Demo one-way SOS without acknowledgement; note it as designed-not-built
- **Day 11:** Cut route check entirely (on the ladder); keep check-in
- **Day 12:** Send-only SMS; skip receive and use stock Messages app demo
- **Day 13:** Cut dashboard acknowledge, cut polling, use refresh button. Or cut server + dashboard entirely — demo step 12 is lost but V0 is safer.

**Never compromise:** offline map, reporting, rules B/C, SOS over mesh, honest offline copy.
