# KaAlerto — project context

Offline-first community flood map and rescue channel for Philippine barangays. Android (Kotlin), sideloaded. Solo developer. Hackathon: 1–30 September 2026, five equally-weighted staged submissions.

**Read `docs/02-prd.md` before proposing product changes.** It is the canonical spec — 17 sections, 43 requirement IDs, ~10 pages.

---

## Current state (condensed 15 Sep 2026)

**The day-by-day build history — what was built, how it was verified, what was found and why — is in `BUILD_LOG.md`.** Read the relevant entry there before changing a feature; it records the reasoning behind many non-obvious choices. This section is only the summary and the things that are easy to undo by accident.

**The demo area is frozen: Barangay San Juan Bautista, San Nicolas, Ilocos Norte.** Every fixture, screenshot and route lives inside `DemoArea.kt`'s bounding box; read its class doc before touching any coordinate. None of it has been checked against a printed barangay map.

**Status by build day** (verified = on the `API34_Test` emulator in airplane mode unless noted):

| Day | Feature | Status |
|---|---|---|
| 0 | Fixtures (19 OSM seed reports, 4 evac centres, 3 routes, OSM extract) | Done |
| 1 | Offline tiles (`OfflineMapPack`, OpenFreeMap Liberty) | Done, verified |
| 2 | Event store (Room, content-hash dedup) + severity markers | Done, verified |
| 3 | Reporting flow (GPS + tap-to-pick, photo hash only) | Done, verified |
| 4 | Reducer (Rules A–D, Wilson, buckets, SX) + detail sheet | Done, verified; `data/ReducerTest.kt` covers it |
| 5 | Home geofence notifications, filters, Storm Mode | Done, verified |
| 6–7 | Nearby Connections mesh | Done; radio hop proven on real phones 18 Sep (report relayed device to device, no server) |
| 8 | SOS flow (hold → context → status → rescue card, QR) | Done, verified |
| 9 | SOS over mesh + acknowledgement | Done; full round trip proven on two real phones 18 Sep. Also syncs over Supabase (redacted); acknowledging shows the location on the map |
| 10 | Official role, second-official gate, evacuation centres | Done, verified |
| 11a | Family check-in circles (QR pairing) | Done on two emulators; QR camera step not proven on hardware |
| 13 | Supabase sync + background push + LAN dashboard | Supabase sync proven on real phones; WorkManager push added 19 Sep. **The self-hosted Node server, phone-side server sync and LAN discovery were removed 19 Sep.** Dashboard: `dashboard/` (Next.js), reads Supabase behind a shared PIN |
| — | PRD §9 registration, profile, drawer, EN/FIL toggle, real-hardware fixes (8–9 Sep) | Done |

**Unmerged local work (15 Sep):** branch `feat/gps-integration` holds the passable-v0 back-port (seed fixes, samples never leave the phone, seeds reload each cold start, plain-language labels, system Back, offline gazetteer, surname required) and GPS integration (map opens on an out-of-area home, "Nasaan ako"/"Demo" jumps, "download here" pack, geocode cache). Progress ledgers: `.worktrees/gps-integration/.superpowers/sdd/*/progress.md`.

### Easy to undo by accident — each one was found the hard way

- **Map/toolchain:** never switch back to `demotiles.maplibre.org` (native crash, maplibre-native#4403); keep `android-sdk-opengl`; do map work on `API34_Test`, not the API 37 AVD (renders black).
- **Mesh:** Nearby runs `setLowPower(true)` (BLE only) — the default `P2P_CLUSTER` mediums made Play Services switch Wi-Fi on every time the app opened; Nearby needs `ACCESS_WIFI_STATE`/`CHANGE_WIFI_STATE`; `MeshRadios` checks the Bluetooth adapter and location *services*, not just permissions. Success paths are silent in logcat by design.
- **SOS:** state transitions are events folded monotonically (`mergeSosState`); unbuilt channels say so — never fake progress; the rescue card raises once per request (`rescueCardRaisedFor`); `sos*` events carry `featureRef = null`; `redactSosOnEgress` strips medical detail and the requester's name on every egress (mesh and Supabase); `MainActivity` is `singleTop`; SOS routes wait for `snapshotsLoaded`; `SosAlertWatcher` does not skip its first emission.
- **Reducer/store:** `FeatureStateDao.upsert` stays `REPLACE` (it is the materialized fold, not the append-only log); TTL purge keeps a 24 h grace (`RETENTION_AFTER_EXPIRY_MS`) so stale roads render grey; the crowd fold excludes officials; the second-official gate applies only when lowering against current worse reports.
- **Roles:** event-sourced flow is parked behind `RoleMode.EVENT_SOURCED = false`; when it flips, delete `ManualRoleScreen` and `setRoleForTesting`. The fold runs in three passes; an official cannot grant official; the roster is seats, not people; role events have a one-year TTL and null-island coordinates. Keep `map/RoleActionStrip.kt`. False-alarm marking only demotes sort order — never hides an alert.
- **Location:** `fetchCurrentLocation` is bounded (6 s + last-known fallback) for SOS/reports; `fetchAccurateLocation` streams up to 15 s for the home. Do not unify them.
- **Fixtures/wire values:** developer commentary lives in `fixtureNote`, never `note`; SOS option values stay printable ASCII (GSM-7).
- **Storm Mode:** re-tints the loaded style in memory (offline packs are style-scoped), skips `kaalerto-` layers, reloads per toggle with `styleEpoch`; the camera is placed once (`cameraPlaced`).
- **Identity:** the full name is stored locally, but only `displayFormOf` ever leaves the device; given name and surname are separate fields; never rewrite old events after a name change (NFR-4).
- **UI:** a fresh `selectedFeatureRef` must not be auto-cleared (`everHadSelectedSummary`); system bars stay visible with one root `windowInsetsPadding` — do not re-hide them; the evac control stays labelled "Silungan".
- **Sync:** only `flood_report`/`confirm`/`dispute`/`official_status` and the redacted `sos*` types go to Supabase (it has no access control); push AND pull have no cursor and no bbox on purpose (carry-forward; a demo-area bbox once silently dropped every real-GPS report); `encodeDefaults = true` is required or PostgREST rejects the batch (`PGRST102`); `SupabaseSyncWorker` repeats the push when the app is closed.
- **Family:** `circle_invite`/`family_checkin` ride the mesh in the clear — disclosed, not fixable without crypto; "my status" reads only this device's own check-ins (`myLastCheckInMs`).
- **i18n:** every string through `tr()`; SOS wire values, the rescue-card banner and exception text stay untranslated on purpose; strings built from a bilingual field's `.fil` need a grep, not just a literal sweep.
- **Process:** run `assembleDebug` (check the APK mtime) before installing to verify; compare devices only after a real uninstall.

---

## Decisions already made — do not re-litigate

These were settled deliberately. Reopen only if the user asks.

| Decision | Why |
|---|---|
| **Supabase is the only server** (the self-hosted Node server was removed 19 Sep) | Supabase was rejected on 5 Sep, reopened by the user on 18 Sep to remove the manual server-address problem, and the self-hosted server was then removed on 19 Sep because mesh worked on real phones and the server needed a saved address plus a shared LAN. What is lost: the zero-internet LAN path (a shelter with Wi-Fi but no internet now relies on mesh alone). What is unprotected: the anon key ships in the APK and the table has no access control, so the LGU dashboard's PIN guards the dashboard's door, not the data. See `sync/SupabaseSyncLoop.kt`, `sync/SupabaseSyncWorker.kt`, `supabase/schema.sql`. |
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

Immutable append-only event store → deterministic reducer → displayed state. Every device holds its own store and computes its own map; **the local DB is a replica, not a cache**. Two devices with the same events must display the same status (NFR-4) — this is the invariant that makes an offline phone trustworthy rather than merely stale. Events reach other devices by three transports: Supabase sync when there is internet, Bluetooth relay via Nearby Connections (low power, BLE only), and SMS (bit-packed, 160 chars — stub only, not built). Deduplication is by content hash, so re-delivery over multiple transports is harmless. Notifications are evaluated locally on every event insert, so they fire with no push server. Full detail in `docs/03-architecture.md`.

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
dashboard/     LGU web console: Next.js + MapLibre GL JS, light mode only. Reads Supabase through its own /api/events route, which checks a shared PIN (DASHBOARD_PIN) server-side. See dashboard/.env.local.example
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
- All Filipino copy is unreviewed by a native speaker. Separately from that review, the 6 September pass found English strings sitting inside Filipino screens where nothing bilingual was intended: `Impassable` in the filter bar, `Unverified`/`Likely` in the detail sheet, `Seed data`, `18 min ago`, and a confirmation rendering as "Kumpirmasyon · Residente 89A7 · **Resident**" — the role appended in English onto a name that already carries it.
- **`Dashboard` (LGU web console) is built (19 Sep)**; `VolunteerRegister` is still unbuilt and exists only inside the parked event-sourced role flow. `QueueOfficial` is built except for its medical column, which day 9's redaction makes impossible rather than pending.
- `docs/05-routing-matrix.md` routes `Onboarding` straight to the map, which was already wrong against PRD §9 and is now wrong in a second way: as of 7 Sep it is not the entry screen at all but an authoring gate. §8 also lists eight routing gaps. The two role landing screens are closed as of 6 Sep (`map/RoleActionStrip.kt`); no cleared-detail screen and no post-submit confirmation remain.
- No field measurement of relay range, delivery rate or battery cost. Named honestly in the pitch rather than implied to be done.
- **The event-sourced role flow is parked** behind `identity/RoleMode.EVENT_SOURCED = false` while roles are being tested by hand; the app ships day 10's self-select toggle until it flips. It also has **no seat-release path** — a claim is one-way, which is what made the flag necessary. Decide whether releasing a seat should exist before going live.
- ~~The day-4 reducer has no test file of its own.~~ **Closed 7 Sep:** `data/ReducerTest.kt` (20 tests) covers Rules A-D, weighting, Wilson confidence, buckets, staleness and the anchor.

---

## Build sequence and gate structure

The project follows a 15-day build schedule divided into five gates (one per September week). Each gate is equally weighted in scoring.

**Offline map tiles** were the single highest-risk task; `OfflineManager` pre-download is verified in airplane mode on the emulator (build day 1). Real-hardware airplane-mode proof is still the bar for calling any feature done. Bundled MBTiles remains the fallback if `OfflineManager` ever fails on a device.

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

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- **Invoke it as `python -m graphify`, not bare `graphify`** — it is a `pip install --user` package and its `Scripts` dir is not on PATH on the dev machine. Same reason `.claude/settings.json`'s hooks use that form.
- For codebase questions, first run `python -m graphify query "<question>"` when graphify-out/graph.json exists. Use `python -m graphify path "<A>" "<B>"` for relationships and `python -m graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `python -m graphify update .` to keep the graph current (AST-only, no API cost). The post-commit git hook also does this automatically.
- The graph began **code-only** (AST of 154 files, no LLM). The post-commit hook also adds a markdown file's heading structure whenever a commit changes it — so far that is only this file. `docs/` (gitignored) and `design/` artboards are not in it, and no document's prose is — for product questions, `docs/02-prd.md` and this file remain the source.
