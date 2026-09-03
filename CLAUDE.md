# KaAlerto — project context

Offline-first community flood map and rescue channel for Philippine barangays. Android (Kotlin), sideloaded. Solo developer. Hackathon: 1–30 September 2026, five equally-weighted staged submissions.

**Read `docs/02-prd.md` before proposing product changes.** It is the canonical spec — 17 sections, 43 requirement IDs, ~10 pages.

---

## Current state (updated 3 Sep 2026)

**The Android project is scaffolded and verified building; no features exist yet.** `./gradlew build` completes green — debug + release APKs, lint clean, unit tests pass. What is there is a Compose scaffold that launches and nothing more, but every dependency needed through build day 13 is wired and resolving (MapLibre, Room + KSP, Nearby Connections, FusedLocation, serialization), so no build day is blocked on dependency setup. `server/` and `dashboard/` are still empty placeholders.

`BUILD_TASKS.md` (repo root) is the day-by-day implementation list, stripped of the submission ceremony in `docs/04-build-plan.md`. `SETUP_CHECKLIST.md` tracks the remaining non-code prep — devices and fixtures.

**Where the schedule actually stands:** `docs/04-build-plan.md` front-loads build days 1–2 into week 1, specifically so the offline-tiles gate gets attempted on ~2 September rather than 15 September. That has not happened yet. Raw arithmetic is 15 build days into a 15-day window with zero buffer, and front-loading is what creates the only slack there is.

**The single highest-risk task is offline map tiles** — MapLibre `OfflineManager` pre-download, verified in airplane mode, with bundled MBTiles as the bulletproof fallback. If it fails, the whole premise fails, and it is worth discovering that now rather than on the 15th. Do this before anything else.

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
tools/         render-artboards.js · final-prd.js · ideation.js · check.py
submissions/   one file per gate; doubles as release notes. README has the gate checklist
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
- **The versions are a matched set** in `android/gradle/libs.versions.toml`: Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.10, KSP 2.3.11, compileSdk/targetSdk 37, minSdk 26. Bump them together.
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
