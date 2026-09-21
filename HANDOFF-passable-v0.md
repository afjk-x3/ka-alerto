# Context handoff — `passable-v0` (updated 14 Sep 2026: V0 released and declared complete)

## Where things stand

- **V0 is done.** The user declared `passable-v0` complete on 14 Sep 2026. No further work is
  planned on this branch; the full app is `feat/event-sourced-roles`, which has more features
  and a different UI.
- **Released:** GitHub Release **"v0 — the offline core"** —
  https://github.com/afjk-x3/ka-alerto/releases/tag/stage-3-v0 — marked *latest*. This is the
  URL to submit for the Stage 3 gate.
  - Tag `stage-3-v0` → `05f358f` (the head of `passable-v0`). It was first created at
    `c2a7030`, then force-moved to `05f358f` with the user's approval when the surname change
    went in, so the tag, the README and the APK all match.
  - Asset `kaalerto-v0.apk`: the **release** build (package `com.macci.kaalerto`, version
    0.1.0, versionCode 1, minSdk 26), not minified, **signed with the Android debug key**
    (user's choice — no keystore). SHA-256
    `2542b5832b7be878f84b6ce0e84ca13374dbe1e7d772462183363fdea2914e31` (GitHub's digest
    matches). A later build signed with a real key will not install over it without an
    uninstall.
  - The release notes still say "Demo video: to be linked here".
- **Branch:** `passable-v0` at `05f358f`, pushed, in sync with `origin`. Only this file is
  untracked (deliberately).
- Scope is the offline core: offline map, reporting, confirm/dispute + reducer,
  notifications, filters, Storm, registration, local-only SOS. **No mesh, no SMS, no server
  sync, no roles.**

## Commits since the 13 Sep handoff (oldest first)

| Commit | What |
|---|---|
| `086858a` | Content kept clear of the status and navigation bars (`enableEdgeToEdge` + safe-drawing insets at the root; bar icons follow Storm). Real-phone finding: targetSdk 37 forces edge-to-edge on Android 15+, which the API 34 emulator does not. |
| `4757c68` | Philippine time everywhere (labels, rescue card, "ulat ngayong araw"); seed ages spread so each recency chip changes the map (008 → 100 min, 005 → 8 h, 013 → ~26 h, 010 → 14 days). |
| `3a2d722` | Place name before the coordinates on the report card, SOS card and QR: offline from the bundled OSM streets/landmarks inside the demo area; the platform geocoder only when online and never on the SOS screen. |
| `56e2f37` | "Nasa labas ka ng demo area" dialog: once per launch when GPS is outside the demo area, and on Mag-ulat with "Pumili sa demo map" / "Gamitin ang lokasyon ko". |
| `1d3c33e` | Home-location pin at the geofence centre (ported from feat `660770f`). |
| `6e3605f` | Release build signed with the debug key. |
| `c2a7030` | README honesty pass: status line, "built in V0" / "designed, not built yet" labels, real V0 screenshots in `screenshots/v0/`, install steps. |
| `92dd97a` | (User, on GitHub) removed the README's "More" section. |
| `bf1695a` | Surname required at registration; a one-name registration from an earlier build re-opens the form, pre-filled. |
| `05f358f` | README: "first name, surname and home barangay, all required". |

The ten earlier commits (`a723ae2` … `8b8c89b`, 12–13 Sep) are unchanged: seed notes, plain-language
"Saan galing" + dated times, bounded GPS, system Back, seed reload on cold start, offline banner,
Storm map tint, Filipino detail sheet, registration, local-only SOS + profile edit.

## Verified

- **Real phone:** the user confirmed the real-phone checklist from the previous handoff is done
  and passed (14 Sep). The released APK was also installed on the user's Infinix phone — see
  "Known gaps" for the security-scan warning it showed.
- **Emulator (API34_Test):** for the 14 Sep fixes, 10/10 scripted checks passed with screenshots
  reviewed — header below the status bar and the action bar above the nav bar; launch notice
  once per launch; both Mag-ulat choices; place names offline ("Sotto Street, Brgy. San Juan
  Bautista…" and "Labas sa demo area"); SOS naming the street; rescue-card time in Philippine
  time with the emulator on GMT; each recency chip showing a different set of markers; home pin
  in the draft and after save + relaunch.
- **Tests:** 68/68 unit tests and lint pass at `05f358f`; each intermediate commit was built and
  tested on its own in a worktree (49 → 56 → 67 → 67 → 67 → 68).
- **Not verified on screen:** the surname-required form (unit-tested only; the emulator was
  closed by then).

## Decisions — don't re-litigate

Still standing from the 13 Sep handoff: SOS on this build is local-only and says so;
registration required at first run with SOS the only way past; barangay blank and required;
seed reports labelled "Halimbawang ulat"; no hamburger; seeds replaced on each cold start; UI
Filipino-only except the rescue card's English line.

New on 14 Sep:
- **Surname is required** (user's decision; matches PRD §9's "first name and last initial").
- **Release APK signed with the debug key**; the release lives on `passable-v0`, tag
  `stage-3-v0`, and the tag was moved to `05f358f` with approval.
- **V0 is complete** — the user declined further V0 changes, including trimming unused
  permissions after the Infinix scan warning.
- **For the back-port to `feat`:** V0's **Philippine-time pinning is not carried over** (`feat`
  keeps the phone's time zone), and the **outside-demo-area GPS notice is deferred** — the user
  wants to discuss integrating GPS properly first (next conversation).

## Next conversation — start here

**GPS integration discussion** (user's request). Instead of V0's notice that explains why the map
stays on San Nicolas, discuss whether the app can handle real GPS locations properly. Facts for
it: the map opens on `DemoArea.centre` and never follows GPS (a following camera makes the demo
unrepeatable); the offline pack covers only the demo area's bounding box (z10–14), while `feat`
also has a home-area offline pack (`f93eeb7`); the sample reports exist only in San Nicolas;
`feat`'s `describePlace` uses the online geocoder; the user tests from Pangasinan.

## Back-port to `feat/event-sourced-roles`

Plan: `docs/superpowers/plans/2026-09-14-backport-passable-v0-to-feat.md` (in the gitignored
`docs/`, so local only; it survives branch switches). It has the context, a side-by-side of the
two UIs, an inventory of all 20 V0 commits (port / already on feat / V0-only / user decision),
and ten test-first tasks. The user is keeping it as a reference for later; it has not been
started.

- **To port:** seed notes, SX name `sotto-street-9` and age spread; keep samples off server sync
  and the mesh relay; seed reload on every cold start; plain-language labels and dated times in
  the detail sheet; system Back; offline place names in front of `feat`'s geocoder; required
  surname.
- **Already on feat:** bounded GPS, Storm tint, home pin, system-bar padding, offline banner,
  registration.
- **Not ported:** Philippine time (user decision), the local-only SOS screen, header profile icon,
  V0 README. **Deferred:** outside-demo-area notice (Task 8).
- **Key caveat:** on `feat`, `eventsToSync` and `relayable` currently send sample reports, and
  received copies are relabelled `mesh`/`server` with the same id — so samples must be excluded
  from both *before* seeds reload on each cold start (Task 2 before Task 3).
- `feat` was read from the local copy at `53aaa6c` (fetch failed on 14 Sep); fetch before
  starting.
- Switching: `git fetch origin`, `git checkout feat/event-sourced-roles`, `git pull --ff-only`.
  `feat`'s Room DB is version 3 vs V0's 2 (both destructive-fallback): the first `feat` debug
  build on the emulator starts with empty report data (the offline map survives); going back to
  a V0 debug build may need the app's data cleared. The phone's release app
  (`com.macci.kaalerto`) is separate from debug builds (`.debug`).

## Open questions for the user

- One nearby "Humupa na" dispute turns a lone flooded report into **SX** (weight 0.7 ≥ 0.5;
  Rule B blocks lowering). Intended, or too strong?
- Should Storm Mode survive an app restart? (It resets to Normal today.)
- Filipino copy is unreviewed by a native speaker, and the demo area is Ilocano-speaking.
- Resolved: the release and tag went on `passable-v0`.

## Known gaps (honest-notes material)

- **Infinix install warning.** The phone's installation-source scan flagged the APK ("quality
  inspection and security audit standard"). Likely causes: sideloaded from GitHub; the public
  Android debug certificate; and the manifest still declaring permissions V0 never uses —
  `SEND_SMS`, `RECEIVE_SMS` plus an exported `SMS_RECEIVED` receiver, five Bluetooth
  permissions, `NEARBY_WIFI_DEVICES`, foreground service. The user chose not to change V0.
- The barangay is stored but not embedded in events (no schema field), so reports read "Juan D.
  · Residente", not PRD §9's name-with-barangay.
- After registering from Tama/Iba na you land on the map, not back in the sheet.
- Conflict rows say "nasa lugar" unconditionally.
- The offline banner can read "Dina-download ang mapa · 42% / 0 tile".
- The outside-demo-area notice appears at launch for anyone not in San Nicolas — including in
  a demo video recorded elsewhere.
- Place names outside the demo area need internet (report screen only; never on SOS).
- Sample report times are re-applied on every launch, so "14 days ago" is relative to app start.
- Mesh, SMS, server sync, roles, evacuation centres, family check-in: not on this branch.

## V0 gate — due Sat 19 Sep (`docs/04-build-plan.md` §8)

- [x] Real-phone airplane-mode test (confirmed by the user, 14 Sep)
- [x] Signed APK (release build, debug key)
- [x] GitHub Release `v0` with the APK and a "what works / what doesn't" section
- [x] Tag `stage-3-v0`
- [x] README honesty pass
- [ ] 60–90 s demo video: open → airplane mode → map works → file a report → SX. Then edit the
      release notes to link it at the top.
- [ ] `docs/04-build-plan.md` §11 demo script still says "Marikina"
- [ ] Submit the release page URL

## Emulator state (API34_Test)

- Closed by the user at the end of the session.
- **If it hangs or exits right after "Loading snapshot 'default_boot'":** the quick-boot snapshot
  was bad on 14 Sep. Kill any leftover `emulator.exe`, `adb kill-server`, delete
  `%USERPROFILE%\.android\avd\API34_Test.avd\multiinstance.lock`, then cold-boot:
  `"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd API34_Test -no-snapshot-load -no-snapshot-save -no-boot-anim`.
  Cold boot keeps the app, its data and the offline map, and fixes the clock (the snapshot had
  it stuck on 12 Sep).
- Installed: the debug build from just before the surname change (it has the time, place-name,
  notice and home-pin fixes). Registration on it is "T" / "Tt" with no surname — the user's own
  test registration; with a surname-required build it would re-open the form.
- Airplane mode on, Wi-Fi on (no internet on the emulator), GPS last set to Sotto Street
  (`adb emu geo fix 120.6060908 18.1715238`), no home location saved, offline map downloaded.

## Build / install / release

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd android && ./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew assembleRelease   # debug-key-signed release APK: app/build/outputs/apk/release/app-release.apk
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

- `gh` is installed at `C:\Program Files\GitHub CLI\gh.exe` and logged in as `afjk-x3`, but
  terminals opened before the install don't have it on PATH — call it by full path.
- Release changes: upload a new asset under a temporary name, check GitHub's `digest`, then delete
  the old asset and rename the new one — the release never goes without an APK. Create new
  releases as drafts first.

## Testing and workflow gotchas

- Git Bash rewrites `/sdcard/...` into a Windows path — prefix adb scripts with
  `MSYS_NO_PATHCONV=1`.
- `dumpsys window | grep mCurrentFocus` can report the app after it has left the foreground; use
  `dumpsys activity activities | grep topResumedActivity`.
- The emulator's fused location provider always has a cached fix, so a "no GPS lock" hang cannot
  be reproduced there.
- `pm clear` / Settings → Clear data wipes the offline pack along with everything else.
- The API 34 emulator does not force edge-to-edge; real Android 15+ phones do (targetSdk 37).
- The network was flaky on 14 Sep (github.com and google.com timing out while api.github.com
  answered): wrap pushes and `gh` calls in retries.
- `cmd | tail` hides `cmd`'s exit status from `&&` chains — check `${PIPESTATUS[0]}`.
- The user edits files directly on GitHub (e.g. the README): fetch before pushing and rebase
  local commits onto theirs; never force-push the branch.
- Commit and push only when the user asks; one commit per fix, each building and passing on its
  own.
