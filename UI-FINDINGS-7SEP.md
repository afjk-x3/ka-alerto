# UI Findings — 7 September 2026

Read-only investigation of six scoped items. All file paths are relative to `android/app/src/main/kotlin/com/macci/kaalerto/`.

---

## 1. SOS "Humihingi ng Tulong" screens

**Two screens use this title:**

| Screen | File:Line | Title | Sticky header? | Exit button |
|---|---|---|---|---|
| SosHoldScreen | `sos/SosHoldScreen.kt:115` | `"Humingi ng tulong"` | No — single non-scrolling `Column` | `"Kanselahin"` at bottom (lines 167–178) |
| SosQueueScreen | `sos/SosQueueScreen.kt:83` | `"Humihingi ng tulong"` | Yes — fixed header + fixed footer, scrollable middle | `"Isara"` in fixed footer (line 179) |

Related screens that do **not** use this title:

- `SosContextScreen.kt` — "Ipinapadala na ang SOS mo"
- `SosStatusScreen.kt` — "Aktibo/Sarado na ang SOS mo"
- `RescueCardScreen.kt` — "KAILANGAN NG SAGIP"
- `SosNearbyScreen.kt` — "May humihingi ng tulong"

**Rationale comments:**

- `SosHoldScreen.kt:54–61` — *"The long press is not friction for its own sake — the artboard says why: 'Kailangan ng mahabang pindot para hindi ito maaksidenteng ma-send sa bulsa mo.'"*
- `SosHoldScreen.kt:78–80` — *"The countdown runs here rather than inside the gesture handler so that releasing early cancels it by flipping `holding`, and so the haptic ticks stay on the same clock as the ring rather than drifting against it."*
- `SosContextScreen.kt:33–45` — *"The single most important thing on this screen is the sentence at the top: the request is already going out."*
- `SosStatusScreen.kt:96–99` — *"Lets the last control scroll clear of the fixed footer. Without it the scroll area ends exactly at the button, so at rest the button is sliced through its own label."*
- `SosNearbyScreen.kt:34–50` — *"Everything about this screen is an exercise in not showing things."*
- `RescueCardScreen.kt:41–56` — *"Three deliberate departures from every other screen in the app: White at full brightness, in every mode. A card a stranger has to read in the dark is worth the power."*

---

## 2. RoleScreen name field

**`RoleScreen.kt` (event-sourced, line 50):** No name field — only displays names read-only in `SeatHeldRow` (line 278) and pending grants (line 173).

**`ManualRoleScreen.kt` (line 379, bench-test toggle):** Display-only name at lines 456–498. Shows `displayFormOf(...)` with a "Baguhin" button that navigates to `Screen.Profile(Screen.Roles)`. Not editable inline.

**`ProfileScreen.kt` (line 108):** Uses shared `NameFields` from `ProfileFields.kt:70` — a real editable two-field input (given name + surname), with save button at line 141.

**Different implementations, same logical field.** RoleScreen's name is read-only; ProfileScreen's is the only editable version. Tapping "Baguhin" navigates away to Profile.

**After tapping a role option:** Immediate write, no confirmation, no navigation. `ManualRoleScreen` calls `roleViewModel.setRoleForTesting(it)` → `LocalIdentity.setRoleForTesting()` (synchronous SharedPreferences write). The screen stays put and recomposes with the updated state. Same for event-sourced `RoleScreen` — all actions (`claimSeat`, `applyAsResponder`, `grant`, `revoke`) are fire-and-forget event writes with no dialog.

**Rationale comments:**

- `RoleScreen.kt:452–455` — *"PRD §9's name, and the one place to correct it. Editable on purpose: a typo caught an hour later would otherwise be permanent in front of the barangay."*
- `ProfileFields.kt:65–68` — *"Two fields, not one. No rule over a single string can tell 'Juan Carlos Santos' from 'Juan Dela Cruz', and guessing wrong prints an initial that belongs to the person's own given name."*
- `RoleScreen.kt:367–377` — *"Delete this whole composable when the flag flips. It is not a fallback and not an escape hatch for demo day; it is a bench tool."*

---

## 3. Map pick-mode and location

**Pick-mode click listener** (`MapScreen.kt:714–737`): Tap immediately invokes `onLocationPicked(latLng)` and navigates away. **No pin preview, no confirm step.**

- Report path (`KaAlertoApp.kt:241–246`): tap → `Screen.Report(lat, lon, null)` directly
- Home pin path (`KaAlertoApp.kt:349–367`): tap → saves to `draftHome`, clears accuracy, navigates back

**Blue dot** (`MapScreen.kt:801–815`): Passive display only. `CameraMode.NONE` (no follow), `RenderMode.NORMAL`. There is **no way to tap the blue dot to file a report** — the "Mag-ulat" action bar button (line 358–395) calls `fetchCurrentLocation` separately.

**No FAB.** The docs day-3 mention of a FAB is reflected as a full-width bottom action bar (`MapActionBar`, lines 484–545), not a floating button. Comment at lines 472–482: *"not a floating rounded FAB."*

**Rationale comments:**

- `MapScreen.kt:80–92` — *"GPS primary, map-tap fallback" — the caller only needs to react to whichever of the two callbacks actually fires.*
- `MapScreen.kt:811–813` — *"Do not follow the user: the map opens on the frozen demo area, and a camera that chases GPS makes the demo unrepeatable."*
- `MapScreen.kt:432–435` — *"Pick-mode serves two callers now — day 3's report location and registration's home pin — so it must not say 'report' in both."*
- `MapScreen.kt:353–358` — *"Open on the pin being corrected, and show where this phone actually is. Without both, somebody is asked to check a pin against a map of a place they are not in, with no dot to check it against."*
- `MapScreen.kt:361–363` — *"Hand-placed, so the GPS accuracy no longer describes it."*
- `MapScreen.kt:149–151` — *"The SOS button had no pending state at all: it awaited a fix and, if none came, simply never navigated. Observed on device as the red button doing nothing."*
- `MapScreen.kt:712–713` — *"Pick-mode (setting a report location) and marker selection are mutually exclusive per current screen state, so only one click listener is ever live."*

---

## 4. NavDrawer destinations and i18n

**Drawer destinations** (`nav/NavDrawer.kt:166–169`):

| Line | Label | Route |
|---|---|---|
| 166 | `"Mapa"` | `onOpenMap()` |
| 167 | `"Papel mo sa barangay"` | `onOpenRoles()` |
| 168 | `"Ang profile ko"` | `onOpenProfile()` |
| 169 | `"Mga silungan"` | `onOpenEvac()` |

Footer at line 176: `"Ang Storm mode at SOS ay nasa mismong screen — hindi kailangang buksan ito."`

**i18n infrastructure: None.**

- `strings.xml` contains only `app_name` — no UI strings.
- Zero `values-fil/`, `values-en/`, or any locale variant resource directories.
- Zero uses of `stringResource()` or `R.string.*` in Kotlin files.
- `Locale` references in Kotlin are all `java.util.Locale` for date formatting and geocoder calls — none for string localization.

**All Filipino strings are hardcoded inline in Kotlin.** ~49 `Text("...")` calls across ~16 files contain Filipino UI copy directly. Examples:

- `"Gaano kalalim?"` — `report/ReportScreen.kt:98`
- `"KAILANGAN NG SAGIP"` — `sos/RescueCardScreen.kt:93`
- `"Malapit ka ba? Tulungan mo kaming i-check."` — `detail/DetailSheet.kt:187`
- `"Itakda ang tahanan"` — `map/HomeRadiusOverlay.kt:31`
- `"Kanselahin"` appears in 4+ files (`DetailSheet`, `OfficialStatusScreen`, `ReportScreen`, `ProfileScreen`, `HomeRadiusOverlay`)

---

## 5. EvacScreen filtering

**No filtering that hides centres by status.** All centres always display. Only ordering logic is distance sort at `evac/EvacCentre.kt:123–125`:

```kotlin
// "Pinakamalapit muna" (EvacCentres-Normal.dc.html). Centres with no known
// distance sort last rather than pretending to be nearest.
.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
```

Header subtitle at `evac/EvacScreen.kt:94`: `"Pinakamalapit muna · nasa phone mo na ito"`.

**Why "Hindi pa bukas" centres are shown** (`evac/EvacCentre.kt:64–69`):

> `[status]` defaults to `NOT_OPEN` on purpose: a centre existing in the fixture is a building that could be opened, not one that is open. Showing it as "Tumatanggap" before any official said so would send people to a locked school.

**Rationale comments:**

- `EvacScreen.kt:40–58` — Three things shown in the artboard are deliberately omitted (routing, route flood count, facility chips) with rationale for each.
- `EvacScreen.kt:232–233` — Capacity is labelled "tantiya" once per card, with the full caveat in the footer.
- `EvacScreen.kt:274–278` — *"OfficialControls: occupancy steps by 10 because an official doing this in a flood is standing in a doorway counting, not typing."*
- `EvacScreen.kt:358–361` — *"Walking estimate at a deliberately slow 4 km/h because evacuation is carrying children through water, not a stroll."*
- `EvacCentre.kt:13–17` — *"Names and coordinates are real and OSM-confirmed; capacityEstimate is not."*
- `EvacSubmit.kt:10–17` — *"Event rides the mesh with featureRef = null so it never appears as a flood marker; only an official may post one, unsigned."*
- `EvacSubmit.kt:43–44` — *"A centre's status is good for 24 hours; the artboard's own footer worries about staleness."*

---

## 6. Report flow — photo, "Gaano kalalim", hamburger

**No photo/camera capture exists.** The only reference is a comment explaining the deliberate omission at `report/ReportScreen.kt:234–237`:

> Report-Normal.dc.html also has an optional-photo row here (FR-2.6). Left out deliberately: photo capture (camera intent, local storage, hash-only relay) isn't built by any day yet, and a tappable row that does nothing would misrepresent what the app can do.

**"Gaano kalalim?"** is the **title of ReportScreen.kt** at line 98 — the main water-depth selection screen. English subtitle at line 99: `"How deep is the water?"`.

**HamburgerButton: Yes, rendered on the "Gaano kalalim" step** at `report/ReportScreen.kt:93–96`, in the same header row as the back arrow and title.

**Rationale comments:**

- `report/WaterLevel.kt:17–22` — Explains why waist AND chest both map to S3 (both artboard and architecture doc agree independently).
- `report/WaterLevel.kt:34–38` — Vehicle tab uses architecture doc text rather than a separate artboard (no artboard fleshes this tab out).
- `report/WaterLevelIllustration.kt:44–51` — Canvas-drawn stick figure replaces the artboard's SVG (SVG is host-specific), with animated water line and continuous ripple.
- `report/ReportSubmit.kt:12–16` — *"Write-straight-to-Local pattern is FR-2.2's core claim, with no outbound queue yet."*
- `report/ReportSubmit.kt:32–34` — *"Geohash fallback for featureRef (no road-network graph exists yet)."*
