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

## Fixtures — still to do

Needed from build day 2. An empty map demos terribly and debugs worse.

- [ ] Freeze one barangay as the demo area. Every fixture, screenshot and route lives there.
- [ ] OSM extract for that area (Geofabrik PH, clipped small) → `android/app/src/main/assets/`
- [ ] Seed fixture: 15–25 reports across all severities, some stale, **at least one deliberately conflicting pair** (that pair is the Rule C demo)
- [ ] Evacuation centres as static JSON, 4–6 entries with coordinates and capacity
- [ ] 2–3 route GeoJSON lines for route checking

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
