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

- [ ] Three physical Android phones, API 26+ (two is the minimum, three demos genuine multi-hop)
- [ ] Developer Mode on each: Settings → About Phone → tap Build Number 7×
- [ ] USB Debugging enabled, and the authorisation dialog accepted on each phone
- [ ] `adb devices` lists all three
- [ ] Two SIMs with load for the SMS demo
- [ ] Power banks and cables for all three

Verify early that **Bluetooth and Wi-Fi can be re-enabled while airplane mode is on** — that is exactly how the demo runs, and the mesh dies without it. Check this on build day 6, not on the 29th.

---

## Fixtures — demo area frozen, JSON fixtures done, one real item outstanding

Needed from build day 2. An empty map demos terribly and debugs worse.

- [x] **Demo area frozen: Barangay San Juan Bautista, San Nicolas, Ilocos Norte** (3 Sep 2026). See `DemoArea.kt` for full provenance — two independent centroid sources agree within ~300 m; there is no official boundary polygon anywhere, so the bounding box deliberately excludes a landmark cluster ~1.3 km west that is plausibly a different barangay's poblacion.
- [x] Seed fixture: 19 reports across S0–S3, real street coordinates (Sotto Street, Josefa Llanes Escoda National Highway, San Nicolas–Laoag Diversion Road, all OSM-verified), timestamps as load-time-relative offsets so they don't go stale, **one deliberately conflicting pair** (`seed-report-018`/`-019`, same spot, S3 vs S0) → `android/app/src/main/assets/seed_data.json`
- [x] Evacuation centres: 4 real, named, OSM-confirmed facilities (3 schools + 1 clinic) inside the frozen area → `android/app/src/main/assets/evacuation_centres.json`. **Capacity figures are placeholders**, explicitly marked as unverified in the file — replace with barangay-provided numbers before this is shown to anyone from the actual barangay.
- [x] 3 route GeoJSONs, real street centrelines from OSM → `android/app/src/main/assets/routes/`
- [ ] **A proper OSM map extract for offline tile building is still not done.** What exists is fixture *data* (points, lines, JSON) referencing real streets — not a downloaded, clipped `.osm.pbf` region extract for building an offline vector-tile pack. That's a separate step (Geofabrik PH extract → clip to `DemoArea.bounds` → tile pipeline), needed before build day 1's bulletproof MBTiles fallback, and no local tool for it (`osmium`/`osmconvert`/`ogr2ogr`) is installed on this machine yet.

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

**Device not detected.** `adb devices`; if it shows `unauthorized`, reconnect and accept the dialog on the phone.

**A dependency bump breaks the build.** The toolchain versions are a matched set. AGP 8.x in particular can never work here — it uses a Gradle internal API removed in Gradle 9.6. Revert to the pins in `libs.versions.toml` and bump deliberately.
