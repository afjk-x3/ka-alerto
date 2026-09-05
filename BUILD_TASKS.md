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

### Day 5: Notifications + filters

- [ ] Home location long-press with radius slider (drawn circle on map)
- [ ] Geofence check on every event insert (entirely local)
- [ ] Notification channels by severity; critical breaks DND
- [ ] Filter bar: severity toggles + recency chips
- [ ] Storm Mode theme toggle (dark mode)
- **DoD:** In airplane mode, insert a report inside home radius → notification fires
- **If behind:** Keep the notification, cut the filter bar

---

## Days 6–10 — V1 (Mesh + SOS)

### Days 6–7: Nearby Connections mesh

**Hard gate — allocate full two days.**

- [ ] Foreground service hosting Nearby client, persistent notification
- [ ] `startAdvertising()` + `startDiscovery()` on `Strategy.P2P_CLUSTER`, auto-accept
- [ ] On connect: exchange event IDs, send diff as JSON
- [ ] On receive: insert with dedupe, **re-share to other peers** (genuine multi-hop)
- [ ] Peer counter in UI ("3 nearby phones connected")
- [ ] Mark received events with `origin: mesh`, increment `hopCount`
- [ ] Detail sheet shows "via mesh · 2 hops"
- **DoD:** Two airplane-mode phones — report on A appears on B. Phone C (out of A's range, in B's) receives via B
- **Critical debugging note:** Nearby needs location *services* on (not just permission). Both devices need Bluetooth and Wi-Fi manually re-enabled after airplane mode (AM turns them off). Verify this on day 6, not day 29.
- **If behind end of day 6:** Fall back to single-hop, drop multi-hop relay, keep moving

### Day 8: SOS flow

- [ ] Press-and-hold with 1.5s haptic countdown ring
- [ ] SOS event written instantly; context screen **does not block** transmission
- [ ] State machine: QUEUED → BEACONING → RELAYED → DELIVERED → ACKNOWLEDGED → EN_ROUTE → RESCUED (plus CANCELLED / SAFE_SELF_RESOLVED)
- [ ] Honest per-channel status: "Server: trying… · SMS: sent · Nearby phones: 2 reached"
- [ ] Lighthouse fallback card: full-screen high-contrast, large coords, people count, medical needs, timestamp, QR code, "sound alarm" button
- **DoD:** SOS on offline phone reaches BEACONING, shows scannable QR card
- **If behind:** Cut strobe and context screen; **keep the QR card** (cheap, memorable)

### Day 9: SOS over mesh + acknowledgement

**The money demo — hard gate.**

- [ ] SOS routes over Nearby at highest priority
- [ ] Receiving device raises critical full-screen alert (distinct from flood notifications: sound, vibration, red)
- [ ] Responder mode toggle exposing SOS list with Acknowledge button
- [ ] Acknowledgement is an event propagating **back** over the mesh
- [ ] Originator screen updates to "Barangay responder has seen your request"
- [ ] Monotonic states: lower state cannot regress a higher one
- **DoD:** Two airplane-mode phones. SOS on A → critical alert on B → acknowledge → A updates. Rehearse 10× before day 10
- **If behind:** Demo one-way SOS; note the ack as designed-not-built. Try hard not to cut it — it's the emotional peak.

### Day 10: Official role + evacuation centres

- [ ] Role toggle (settings): Resident / Responder / Barangay Official
- [ ] Official actions on detail sheet: Verify · Mark closed · Mark cleared (override crowd severity)
- [ ] Official severity gets **Official** bucket, distinct badge
- [ ] Contradicting crowd reports stay visible with a note: *"3 residents report worse conditions than the official status"*
- [ ] Evacuation centres from static JSON: distinct pins, distance-sorted list, detail with capacity
- [ ] Officials can change evacuation status (tiny event that rides the mesh)
- **DoD:** Switch to Official, mark a conflicting road cleared, watch SX resolve and badge appear

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
| 5 | Notifications + filters | In-radius report triggers notification | ⬜ |
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
