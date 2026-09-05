# KaAlerto — project context

Offline-first community flood map and rescue channel for Philippine barangays. Android (Kotlin), sideloaded. Solo developer. Hackathon: 1–30 September 2026, five equally-weighted staged submissions.

**Read `docs/02-prd.md` before proposing product changes.** It is the canonical spec — 17 sections, 43 requirement IDs, ~10 pages.

---

## Current state (updated 5 Sep 2026)

**The demo area is frozen: Barangay San Juan Bautista, San Nicolas, Ilocos Norte.** Every fixture, screenshot and route lives inside `DemoArea.kt`'s bounding box. Read that file's class doc before touching any coordinate in the project — it records the sourcing (two independent centroid sources agreeing within ~300 m, no official boundary polygon exists anywhere, and a landmark cluster ~1.3 km west was deliberately excluded because it is plausibly a *different* barangay's poblacion). **None of this has been walked or verified against a printed barangay map** — confirm with someone who knows the area before it goes anywhere near the barangay itself.

**Day 0 fixtures are now fully done, including the OSM tile extract.** `android/app/src/main/assets/` has real, OSM-sourced seed reports (19, across S0–S3, including the deliberate conflicting pair for the Rule C/SX demo), evacuation centres (4 real named facilities, capacity figures explicitly marked as placeholders), and route GeoJSONs (3 real street centrelines). `tools/osm-extract/demo-area.osm.pbf` (fetched 5 Sep 2026) is the region-clipped OSM extract for the offline tile pipeline — 10,239 nodes, 1,787 ways, 3 relations, verified against the same real streets and landmarks the other fixtures reference. It came from the official OSM API's `/api/0.6/map` bbox endpoint rather than a Geofabrik PH download clipped with `osmium`/`osmconvert`/`ogr2ogr` (none of which were installed, and the PH extract alone is ~600 MB) — same bbox-clipped `.osm.pbf` result, at the cost of only `pip install osmium` for the XML→PBF conversion. See `tools/osm-extract/README.md`. This is still just the source extract, not a built tile pack — the day 1 MBTiles-fallback build step is separate and not needed since `OfflineManager` already works (below).

**Build day 1 (offline map tiles) is DONE and verified — on the API 34 emulator, not API 37.** `OfflineMapPack.kt` and `MapScreen.kt` implement the `OfflineManager` pre-download path. Full DoD confirmed by screenshot: airplane mode on, force-stop, cold relaunch, map renders the real demo area (`San Juan Bautista` label, Sotto Street, evacuation centres) from the 8-tile offline pack, zero network. Style is OpenFreeMap's Liberty (`tiles.openfreemap.org`) — not yet the self-hosted OSM-derived style the architecture doc calls for, but real cartographic detail with worldwide maxzoom-14 coverage, not the bundled-MBTiles fallback (not needed; `OfflineManager` works fine).

**Build day 2 (event model + local store) is DONE and verified — same `API34_Test` emulator.** Room schema for `Event` and `FeatureState` (`android/.../data/`), an `EventRepository` deduplicating by content-hash primary key, a seed loader for `assets/seed_data.json`, and severity-colored map markers wired into `MapScreen` via a new `MapViewModel`. DoD confirmed by screenshot: airplane mode, force-stop, cold relaunch, all 19 seeded reports render at the correct real-world locations in the correct severity colors (amber S1, orange S2, red S3, blue S0), zero network. `FeatureState` is schema-only so far — nothing populates it yet; the reducer (Rules A–D, SX conflict detection) is day 4's job, and the seeded conflicting pair currently just renders as a single S3 dot with S0 occluded underneath.

**Build day 3 (reporting flow) is DONE and verified — same `API34_Test` emulator, airplane mode, cold relaunch.** `report/` holds the whole flow: `WaterLevel.kt` (body/vehicle depth scales — body copy and severity mapping lifted verbatim from `design/artboards/Report-Normal.dc.html`'s own embedded data), `WaterLevelIllustration.kt` (Canvas-drawn dynamic illustration, since the design's is SVG specific to the design-canvas host), `ReportScreen.kt`, `ReportSubmit.kt`. `location/LocationFetcher.kt` does one-shot GPS via `FusedLocationProviderClient`, falling back to a tap-to-pick mode on the existing map (`map/MapScreen.kt` gained a FAB and a pick-mode click listener — no second `MapView`). Both the GPS path and the map-tap fallback were exercised end to end: a filed report writes to Room immediately and appears on the map with no extra plumbing, since the map already observes the event table as a `Flow`. **`identity/LocalIdentity.kt` is a stopgap** — no `BUILD_TASKS.md` day schedules the real registration/onboarding screen (PRD §9) yet, so every locally-authored report currently carries a generated placeholder name until that screen exists.

**One MapScreen refactor rode along with day 3, not a day 3 requirement itself:** the `AndroidView` `update` block used to call `map.setStyle(...)` on every recomposition (reloading the whole style each time the event list changed, a harmless but wasteful pattern from day 1/2). It now guards on a remembered `MapLibreMap` reference and calls `setStyle` once; markers push reactively via a `LaunchedEffect` instead. Re-verified the day 1/2 airplane-mode DoD after this change — still passes.

**Build day 4 (confirm/dispute + reducer — "the intellectual core") is DONE and verified — same `API34_Test` emulator, airplane mode, cold relaunch.** `data/Reducer.kt` is a pure fold over a feature's events implementing a deliberately simplified reading of `docs/03-architecture.md` §4-5 (role-based weight, not signed certificates — no crypto per ground rule 4): Rules A-D, Wilson-score confidence, and the four buckets. Map markers (`map/EventMarkers.kt`) now render one per *feature* (reducer output), not one per raw event as in day 2 — the seeded conflicting pair renders as a distinct purple **SX** marker, confirmed by screenshot. `detail/DetailSheet.kt` (tap a marker to open) shows the day-4 detail sheet, copy and layout lifted from `design/artboards/DetailConfirmed-Normal.dc.html`/`DetailConflict-Normal.dc.html`; confirm/dispute actions (`detail/ConfirmDisputeSubmit.kt`) write straight to Room and the whole chain — insert → Flow → reducer → recomposition — updates the open sheet live with no manual refresh. Verified: confirming a seeded S2 report moved its bucket Unverified → Likely in front of the camera; disputing with "cleared now" correctly wrote S0 but did not by itself flip the display (Rule B, one dispute isn't enough). New reports (day 3's `ReportSubmit.kt`) now get a geohash-8 `featureRef` instead of `null`, since the reducer needs something to group same-spot reports by. `ui/theme/SeverityColors.kt` (consolidated in day 3) gained an `SX` entry so the conflict badge and marker share the same purple rather than the badge falling back to a generic "unknown" grey.

**Build day 5 (notifications + filters) is DONE and verified — same `API34_Test` emulator, airplane mode, cold relaunch.** Long-press the map to draft a home location (`geofence/HomeLocationStore.kt`), adjust a 100-1000 m radius on a live slider with a real geographic circle drawn on the map (`map/GeofenceCircle.kt` — a generated polygon, not `CircleLayer`'s pixel-space radius, which doesn't scale correctly with zoom), save it. `geofence/GeofenceNotifier.kt` runs for the app's process lifetime (there's no foreground service yet — day 6-7's mesh service is the eventual right home for this), diffing the event table's own `Flow` for newly-appeared reports and firing a local notification (`notification/FloodNotifier.kt`, two channels by severity, S3 bypasses DND) when one lands inside the saved radius. Verified end to end: filed a report 33 m from a saved home point, in airplane mode, after a cold relaunch — notification fired immediately, zero network. The map also gained a filter bar (`map/FilterBar.kt`: severity toggles + recency chips, live-filtering markers) and a manual Storm Mode toggle (`MainActivity.kt` now owns the dark/light state, passed down to `KaAlertoTheme` — deliberately not `isSystemInDarkTheme()`, since Storm is a declared condition per the PRD, not a phone setting).

**Build days 6-7 (Nearby Connections mesh) are CODE COMPLETE but the DoD is NOT MET — and the distinction matters more here than on any previous day.** `mesh/` holds the whole thing: `MeshService.kt` (foreground service, `connectedDevice` type, low-importance persistent notification with a stop action, advertising + discovery on `Strategy.P2P_CLUSTER`, auto-accept), `MeshProtocol.kt` (the JSON wire format — an *anti-entropy* exchange, where peers trade event-ID manifests and send only the diff, batched by measured size against Nearby's 32 KB payload cap), `MeshRadios.kt` and `MeshPermissions.kt`. Received events are stored with `origin = "mesh"` and `hopCount + 1`, and whatever was genuinely new to this device is re-shared to every *other* connected peer — that is the multi-hop leg, and it terminates because a report coming back round a cycle is already known, so nothing is stored and nothing is forwarded. `Event` gained `@Serializable` (the row *is* the wire format — a replicated table needs no transport DTO), `EventDao` gained one-shot `allIds()`/`all()` for the diff, and the map header gained a peer-count line. **Do not describe the mesh as working.** It has never moved a report between two devices: that needs the three physical phones, which are still the blocking setup item (`SETUP_CHECKLIST.md`). What *was* verified on `API34_Test`, in airplane mode after a cold relaunch: the service starts as a foreground service, Nearby accepts both advertising and discovery, the peer counter renders in both Normal and Storm, and the radio watcher behaves. 16/16 unit tests and lint pass.

**Two things the emulator caught on day 6 that would otherwise have surfaced on demo day — both fixed, do not revert:**

1. **Nearby also needs `ACCESS_WIFI_STATE` and `CHANGE_WIFI_STATE` in the manifest**, on top of the Bluetooth and `NEARBY_WIFI_DEVICES` permissions that were already there from day 0. It needs them for the Wi-Fi Direct/hotspot legs it uses to upgrade a Bluetooth connection, even though this app never touches Wi-Fi itself. Both are install-time, so nothing prompts, and nothing fails at build or install time — `startAdvertising()` just fails at runtime with `MISSING_PERMISSION_CHANGE_WIFI_STATE` (8033).
2. **`startAdvertising()` succeeding does not mean the mesh can reach anyone.** Nearby brings up whichever mediums it can and still calls back successfully with the useful ones dead — observed directly, with Wi-Fi LAN advertising failing underneath a successful callback. So `MeshRadios.kt` checks the Bluetooth adapter and location *services* (not just the permission — BUILD_TASKS.md days 6-7 flags that exact confusion) and `MeshService` watches `BluetoothAdapter.ACTION_STATE_CHANGED` / `LocationManager.MODE_CHANGED_ACTION`. Two consequences, both deliberate: the header says "Buksan ang Bluetooth para sa mesh" rather than a false "Naghahanap ng kalapit na phone", and switching Bluetooth back on after airplane mode restores the mesh in about a second with **no app restart** — which is precisely the manual step the day 6-7 debugging note warns the demo depends on.

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
- Rescue-card QR is a drawn placeholder, not scannable.
- All Filipino copy is unreviewed by a native speaker.
- `docs/05-routing-matrix.md` §8 lists eight routing gaps — role landing screens with no inbound links, no cleared-detail screen, no post-submit confirmation.
- No field measurement of relay range, delivery rate or battery cost. Named honestly in the pitch rather than implied to be done.

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
