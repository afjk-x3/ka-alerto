# Design

29 hi-fi artboards, 360 × 800, covering three operating modes and the shared SOS path.

**[Open the live canvas →](https://claude.ai/code/artifact/f1ee7d2c-1462-4788-bb92-5ed9b289f84a)** — all screens on one pan-and-zoom canvas, with annotations.

Routing between screens is specified in the routing matrix, which ships with the Stage 2 submission documents rather than in this repository.

---

## Three modes

| Mode | Ground | When |
|---|---|---|
| **Normal** | `#FFFFFF` / canvas `#F7F5F2` | Calm weather, full feature set |
| **Storm** | `#0D0F12` | Night, rain, glare — reduced motion, larger targets |
| **Survival** | True black | Critical battery — map and SOS only, everything else visibly suspended |

**Severity colour never changes with the mode**, because a colour meaning "impassable" can't mean something else in a different theme:

`S1 #F2A93B` passable with caution · `S2 #E4682B` impassable for cars · `S3 #C42B2B` impassable for all · `S0 #2F7FBF` cleared · `SX` hatch for conflicting

**Two deliberate exceptions to mode theming:** SOS chrome is always urgent-styled regardless of mode, and the rescue card stays white regardless of battery state — a card a stranger has to read in the dark is worth the power.

---

## Screen index

### Core screens — mode matrix

| Screen | Normal | Storm | Survival |
|---|:---:|:---:|:---:|
| Map | `Map-Normal` | `Map-Storm` | `Map-Survival` |
| Report | `Report-Normal` | `Report-Storm` | — |
| Detail · confirmed | `DetailConfirmed-Normal` | `DetailConfirmed-Storm` | — |
| Detail · conflicting | `DetailConflict-Normal` | `DetailConflict-Storm` | — |
| Notifications | `Notifications-Normal` | `Notifications-Storm` | — |
| Evacuation centres | `EvacCentres-Normal` | `EvacCentres-Storm` | — |

### SOS path — mode-independent

`SOSHold` → `SOSContext` → `SOSStatus` → `RescueCard`

The rescue card is a state, not a tap: it appears automatically when every transport has failed.

### Supporting screens

| Screen | Purpose |
|---|---|
| `Onboarding` | First run — name, home barangay, and an SOS control for anyone installing mid-flood |
| `RouteCheck` | Is this route passable, and if not, what else |
| `FamilyCheckin` | Household circle, single-tap "I'm safe" |
| `SOSNearby` | What a resident sees when someone nearby needs help |
| `VolunteerRegister` | Volunteer application — activated by the barangay, never self-granted |
| `QueueVolunteer` | Rescue queue at responder tier — no medical detail |
| `QueueOfficial` | Rescue queue at official tier — includes medical context |
| `OfficialVerify` | Issue an official status from the field, offline |
| `OfficialReverse` | Reverse another official's status; second-official gate on lowering severity |
| `Dashboard` | LGU web console, 1440 × 900 — built last |

### Reference sheets

`Foundations` — type scale, spacing, components · `Palettes` — full token set across all three modes

---

## Rendering artboards

Artboards are `.dc.html` files that normally render inside the canvas host, which supplies `support.js` and resolves `{{bindings}}`, `sc-for` and `sc-if`. To render one standalone — for a screenshot, say — strip the host wrappers and substitute the bindings:

```bash
node tools/render-artboards.js design/artboards <output-dir> Map-Storm Report-Normal SOSStatus
```

Then screenshot with headless Chrome at 2× for a crisp image:

```bash
chrome --headless=new --hide-scrollbars --force-device-scale-factor=2 \
  --window-size=360,800 --screenshot=out.png file:///path/to/Map-Storm.html
```

The three images in `screenshots/` were produced this way.

---

## Known gaps

- The rescue-card QR is a drawn placeholder, not a scannable code.
- All Filipino copy is unreviewed by a native speaker.
- `OfficialVerify`, `QueueVolunteer`, `QueueOfficial` and `SOSNearby` have no inbound links — they are role landing screens reached by signing in as that role or tapping a notification, not by navigation from a resident screen.
- Storm mode has no `RouteCheck`; the supporting screens are Normal-only by design decision.
