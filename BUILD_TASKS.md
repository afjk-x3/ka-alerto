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
- [ ] **OSM map extract for offline tile building** — still not done. The fixtures above are data referencing real streets, not a downloaded/clipped `.osm.pbf` for the tile pipeline. Needed before the bundled-MBTiles fallback in build day 1. No `osmium`/`osmconvert`/`ogr2ogr` installed locally yet.

### Node server skeleton
- [x] Express app scaffolded, `node:sqlite` database initialized (`server/src/db.js`, `server/src/server.js`)
- [x] Basic CORS and JSON parsing middleware — CORS hand-rolled (no `cors` package; Express is the only npm dependency per CLAUDE.md)
- [x] **Went further than day 0 needed**: both day-13 endpoints are implemented and tested, not just scaffolded — `POST /events/batch` (idempotent on event ID) and `GET /events?bbox=&since=&limit=` (bbox-scoped, cursor-paginated), plus `GET /health`. 9/9 tests pass (`node --test`), and a real server process was smoke-tested end-to-end against the actual seed fixture: 19 events posted, round-tripped via GET with the conflict pair intact, re-POST confirmed idempotent, and data confirmed to persist across a process restart.
- [ ] **Not done**: no signature/role verification (matches ground rule 4 — no crypto, no auth, this hackathon build trusts client-supplied content, documented inline in `db.js`); the `cursor` returned by `/events/batch` is a single global cursor, not per-device-region as `docs/03-architecture.md` §392 describes — an intentional simplification for the demo's one region, noted in code.

---

## Days 1–2 — Offline Map (Critical Path)

### Day 1: Map scaffold + offline tiles

**Choice: OfflineManager OR bundled MBTiles — decide by noon.**

**OfflineManager path (3h):**
- [ ] MapLibre integrated, single screen
- [ ] Setup button triggering `OfflineManager.createOfflineRegion()` over demo bbox
- [ ] Tiles persist to device storage
- [ ] Location permission and FusedLocation blue dot
- **DoD:** Airplane mode on, force-stop app, relaunch → map renders offline, blue dot appears

**Bundled MBTiles path (5h):**
- [ ] MBTiles in `assets/`, copy to app storage on first launch
- [ ] Local style JSON pointing at copied MBTiles
- [ ] Location permission and blue dot
- **DoD:** Same as above, with zero network dependency

**Decision:** If you hit 3pm and tiles aren't loading, abandon current path and switch before day ends.

### Day 2: Event model + local store

- [ ] Room database schema:
  - `Event`: id, type, lat, lon, featureRef, severity, waterLevel, authorId, authorRole, timestampMs, expiresAt, origin, hopCount
  - `FeatureState`: featureRef, severity, confidence, bucket, isConflicted, lastReportMs
- [ ] `EventRepository` with deduplication by content-hash ID (5 lines, use immediately)
- [ ] Seed loader reading fixture JSON on first launch
- [ ] Map markers colored by severity from seeded events
- **DoD:** App launches offline, shows 20 seeded markers in correct colors at correct places

---

## Days 3–5 — V0 Core (Offline Reporting)

### Day 3: Reporting flow

- [ ] Water-level picker: icon grid with body silhouette (ankle/knee/waist/chest) + vehicle icons
- [ ] Location input: GPS primary, map-tap fallback (skip snap-to-road)
- [ ] Auto-derive severity from water level (S0–S3), show it, allow override
- [ ] Report writes to local DB immediately, appears on map instantly
- **DoD:** In airplane mode, file a report in <15 seconds and see it on the map

### Day 4: Confirm/dispute + reducer

**The intellectual core — give this a full day.**

- [ ] Detail sheet: severity, water level, age, confirm/dispute counts, confidence bucket, source
- [ ] Confirm action, dispute action with follow-up (cleared now / worse / shallower / wrong location)
- [ ] Reducer as pure function:
  - Weighted counts: weight = role × proximity × exponential time decay
  - **Rule B:** De-escalation needs ≥2 proximate confirmations OR one official event
  - **Rule C:** Substantial weight on both dangerous and safe readings → SX (conflicting, hatched)
  - Buckets: Unverified / Likely / Confirmed / Official
  - Stale: desaturated, dashed, age label ("Last confirmed 3h ago")
- **DoD:** Seeded conflicting pair renders as SX. Confirming moves the bucket visibly
- **If behind:** Rules B and C only, skip decay refinement

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
| 2 | Event store + seeding | 20 markers on map, right colors, right places | ⬜ |
| 3 | Reporting flow | Report filed in <15s in airplane mode | ⬜ |
| 4 | Confirm/dispute + reducer | Conflicting pair renders as SX, confirming moves bucket | ⬜ |
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
