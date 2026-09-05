# Setup Checklist

## Toolchain — done

Verified 3 September 2026 on this machine. `./gradlew build` completes green: debug + release APKs, lint clean, unit tests pass.

- [x] Gradle wrapper committed (9.7.1) — no separate Gradle install needed
- [x] AGP 9.4.0 / Kotlin 2.4.10 / compileSdk 37 / minSdk 26, pinned in `android/gradle/libs.versions.toml`
- [x] All dependencies resolving: MapLibre, Room + KSP, Nearby Connections, FusedLocation, serialization
- [x] `android/local.properties` written with this machine's SDK path

On a different machine, copy `android/local.properties.example` to `local.properties` and set `sdk.dir` — backslashes doubled. That is the only per-machine step.

See [`android/README.md`](android/README.md) for why each version is pinned where it is.

---

## Devices — still to do

Nearby Connections needs real radios; **the emulator cannot do this.**

**This is now the single blocking item.** Build days 6–7's mesh code is written and running (`android/.../mesh/`), and everything about it that can be checked without a second radio has been — the service starts, Nearby accepts advertising and discovery, the peer counter renders, and the whole thing behaves correctly as Bluetooth is switched off and back on in airplane mode. What is still unproven is the only thing that matters: that a report actually crosses from one phone to another, and then relays to a third. **Nothing about the mesh should be described as working until these phones exist.**

- [ ] Three physical Android phones, API 26+ (two is the minimum, three demos genuine multi-hop)
- [ ] Developer Mode on each: Settings → About Phone → tap Build Number 7×
- [ ] USB Debugging enabled, and the authorisation dialog accepted on each phone
- [ ] `adb devices` lists all three
- [ ] Two SIMs with load for the SMS demo
- [ ] Power banks and cables for all three

Verify early that **Bluetooth and Wi-Fi can be re-enabled while airplane mode is on** — that is exactly how the demo runs, and the mesh dies without it. Check this on build day 6, not on the 29th.

---

## Emulator for map work — done (with a caveat)

For anything that isn't Nearby Connections (map rendering, offline packs, UI), an emulator is fine — but not every emulator image on this machine.

- [x] **`API34_Test` AVD created** — Android 14.0 ("UpsideDownCake"), `google_apis_playstore`/x86_64. **Use this one for map work.**
- [x] `sdkmanager`/`avdmanager` installed at `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\` — weren't present before; only the emulator binary and two API-37 system images existed.
- [ ] **Do not use the original `Medium_Phone` AVD (Android 17.0 "CinnamonBun", API 37.0) for map testing.** MapLibre renders nothing on it — solid black, no crash, no error, on both the Vulkan and OpenGL backends — root-caused by elimination, not yet explained upstream. See `CLAUDE.md` for the full isolation process. It may be fine for non-map testing; not verified either way.

To create another AVD later:
```bash
sdkmanager --sdk_root="$SDK" "platforms;android-34" "system-images;android-34;google_apis_playstore;x86_64"
avdmanager create avd --name "MyAVD" --package "system-images;android-34;google_apis_playstore;x86_64" --device "medium_phone"
```
(License acceptance needs real stdin redirection — piping via PowerShell's `|` didn't work reliably; `sdkmanager --licenses < yesfile.txt` from Git Bash did.)

---

## Fixtures — demo area frozen, JSON fixtures done, one real item outstanding

Needed from build day 2. An empty map demos terribly and debugs worse.

- [x] **Demo area frozen: Barangay San Juan Bautista, San Nicolas, Ilocos Norte** (3 Sep 2026). See `DemoArea.kt` for full provenance — two independent centroid sources agree within ~300 m; there is no official boundary polygon anywhere, so the bounding box deliberately excludes a landmark cluster ~1.3 km west that is plausibly a different barangay's poblacion.
- [x] Seed fixture: 19 reports across S0–S3, real street coordinates (Sotto Street, Josefa Llanes Escoda National Highway, San Nicolas–Laoag Diversion Road, all OSM-verified), timestamps as load-time-relative offsets so they don't go stale, **one deliberately conflicting pair** (`seed-report-018`/`-019`, same spot, S3 vs S0) → `android/app/src/main/assets/seed_data.json`
- [x] Evacuation centres: 4 real, named, OSM-confirmed facilities (3 schools + 1 clinic) inside the frozen area → `android/app/src/main/assets/evacuation_centres.json`. **Capacity figures are placeholders**, explicitly marked as unverified in the file — replace with barangay-provided numbers before this is shown to anyone from the actual barangay.
- [x] 3 route GeoJSONs, real street centrelines from OSM → `android/app/src/main/assets/routes/`
- [x] **OSM map extract for offline tile building — done 5 Sep 2026.** `tools/osm-extract/demo-area.osm.pbf`, clipped to `DemoArea.bounds`: 10,239 nodes, 1,787 ways, 3 relations, verified to contain the same real streets and landmarks the fixtures already reference. Fetched via the official OSM API's `/api/0.6/map` bbox endpoint rather than a Geofabrik PH download + `osmium`/`osmconvert`/`ogr2ogr` clip (none of which were installed) — same result, no 600 MB country download, no CLI tool install beyond `pip install osmium` for the XML→PBF conversion. See `tools/osm-extract/README.md` for the full rationale and regeneration instructions. This is still just the source extract — the day 1 MBTiles-fallback build step itself (tile pack from this file) is separate and still not done.

**Before this goes anywhere near the actual barangay:** someone who knows the area should confirm the boundary and the evacuation-centre list. Everything above was built from OSM and PhilAtlas without ground-truth verification.

---

## First run

```bash
cd android && ./gradlew installDebug
```

- [ ] App launches and shows the KaAlerto scaffold
- [ ] Then start day 1 of [`BUILD_TASKS.md`](BUILD_TASKS.md) — offline map tiles, the project's hard gate

---

## Troubleshooting

**Gradle sync fails in Android Studio.** Make sure you opened `android/`, not the repository root. The repo root has no Gradle build.

**`sdk.dir` not found.** `local.properties` is a Java properties file — a single backslash is an escape character, so `C:\Users\...` silently becomes `C:Users...`. Double them or use forward slashes.

**Android Studio's sync fails with "incompatible version (AGP x.x.x)... Latest supported version is AGP 9.3.0" even though `./gradlew build` succeeds on the command line.** This actually happened once already — Gradle's CLI has no opinion about which AGP version an IDE can open, but Studio's sync does, and it hard-refuses anything newer than what that Studio build knows about. The pin in `android/gradle/libs.versions.toml` (currently AGP 9.3.2) is the version Studio 2026.1.3 actually accepts — don't bump it on the strength of a green CLI build alone; check Studio's own sync error (or `idea.log` under `%LOCALAPPDATA%\Google\AndroidStudio<version>\log`, search for `latest.known.compatible.agp.version`) first.

**`adb` not recognized in PowerShell / a fresh terminal.** `platform-tools` isn't on Windows PATH by default. Either add it permanently (`C:\Users\<you>\AppData\Local\Android\Sdk\platform-tools`, via System Properties → Environment Variables), or just use Android Studio's own Run ▶ button and Logcat panel instead of raw `adb` — that's the easier path day to day and doesn't need PATH set up at all.

**Device not detected / `installDebug` fails with "No connected devices!"** Emulators don't stay running between sessions — check `adb devices` (or Android Studio's device dropdown) before assuming a build problem. If a real device shows `unauthorized`, reconnect and accept the dialog on the phone.

**Launching via `adb shell am start` fails with "Activity class ... does not exist."** The debug build installs under a different package than you'd expect: `app/build.gradle.kts` sets `applicationIdSuffix = ".debug"` on the debug build type, so it installs as `com.macci.kaalerto.debug`, not `com.macci.kaalerto` — while the activity's own class name is unaffected and stays `com.macci.kaalerto.MainActivity`. The working command is:
```
adb shell am start -n com.macci.kaalerto.debug/com.macci.kaalerto.MainActivity
```
Simplest fix: just use Android Studio's Run ▶ button, which resolves this automatically.

**A dependency bump breaks the build.** The toolchain versions are a matched set. AGP 8.x in particular can never work here — it uses a Gradle internal API removed in Gradle 9.6. Revert to the pins in `libs.versions.toml` and bump deliberately.
