# KaAlerto Android

Offline-first flood map and rescue channel. Open **this** folder in Android Studio — it is the Gradle root, not the repository root.

## Toolchain

Verified building on 3 September 2026. These versions are a set — bump them together, not individually.

| | Version | Why it is pinned there |
|---|---|---|
| JDK | 25 (Android Studio JBR) | What Studio ships; nothing else is installed |
| Gradle | 9.7.1 | Needed for JDK 25 |
| AGP | 9.4.0 | **AGP 8.x cannot be used** — it calls `InternalProblems`, a Gradle internal API removed in Gradle 9.6 |
| Kotlin | 2.4.10 | Compose + serialization plugins only; see below |
| compileSdk / targetSdk | 37 | The only platform installed, and there is no `cmdline-tools` to fetch another |
| minSdk | 26 | Covers realistic budget handsets |

**AGP 9 has built-in Kotlin support.** Applying `org.jetbrains.kotlin.android` is now an error. The Compose and serialization plugins are still applied explicitly.

All versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Setup

1. Copy `local.properties.example` to `local.properties` and set `sdk.dir`.
   It is a Java `.properties` file, so **backslashes must be doubled**: `C\:\\Users\\you\\AppData\\Local\\Android\\Sdk`.
2. Open this folder in Android Studio, or build from the CLI below.

The Gradle wrapper is committed, so no separate Gradle install is needed.

## Building

```bash
./gradlew assembleDebug
```

```bash
./gradlew build
```

`build` runs assemble (debug + release), lint and unit tests. Lint is configured to fail on errors — that is deliberate, not an accident to work around.

Artifacts land in `app/build/outputs/apk/`.

### Installing on a device

```bash
./gradlew installDebug
```

The debug build uses applicationId suffix `.debug`, so it installs alongside a release build rather than replacing it — useful when comparing behaviour on the same handset.

## Layout

```
settings.gradle.kts          Gradle root — includes :app
build.gradle.kts             plugin versions, all `apply false`
gradle.properties            JVM args, configuration cache
gradle/libs.versions.toml    every dependency version
gradlew, gradlew.bat         committed wrapper
app/
  build.gradle.kts           module config
  lint.xml                   narrow, documented suppressions only
  proguard-rules.pro
  src/main/
    AndroidManifest.xml      permissions + uses-feature (all optional)
    kotlin/com/macci/kaalerto/
      MainActivity.kt
      ui/theme/              Color, Type, Theme
      broadcast/SmsReceiver.kt
    res/
```

## What exists so far

A Compose scaffold that launches, and nothing else. Every dependency needed through build day 13 is wired and resolving — MapLibre, Room + KSP, Nearby Connections, FusedLocation, serialization — so no build day is blocked on dependency setup.

See [`../BUILD_TASKS.md`](../BUILD_TASKS.md) for the day-by-day plan. Day 1 is offline map tiles, and it is the project's hard gate.

## Notes that will bite you later

- **Every `uses-feature` is `required="false"`.** The app's claim is that it works when each transport is unavailable; a required feature would contradict that and block installs on devices without telephony.
- **The SMS receiver is guarded by `BROADCAST_SMS`.** Without it, anyone can spoof an `SMS_RECEIVED` intent and inject a fake flood report or SOS.
- **The adaptive icon must stay in `mipmap-anydpi-v26`.** Lint suggests merging it into `mipmap-anydpi`; doing that makes AAPT2 fail to resolve `@mipmap/ic_launcher`. Suppressed with a note in `app/lint.xml`.
- **Severity colours live in `ui/theme/Color.kt`, not `colors.xml`.** They are mode-independent by design — a colour meaning "impassable" must not mean something else in Storm mode.
- **R8 is off for release.** Turn it on only when there is slack; it adds failure modes that are miserable to debug under time pressure.
