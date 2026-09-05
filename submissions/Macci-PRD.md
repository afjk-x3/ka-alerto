<!--
Living copy of the PRD, kept in the repo (unlike docs/, which is gitignored) so it can be
updated alongside the code as development proceeds. Synced from docs/02-prd.md as of
5 Sep 2026. The rendered submission artifact is Macci-PRD.pdf in this same folder.

Update policy: don't edit this file's content unilaterally mid-development. Surface the
proposed change and get an explicit decision first, then update this file and
docs/02-prd.md together so they don't drift.
-->

# KaAlerto — Final PRD

**A community flood-intelligence and rescue-coordination application for Philippine barangays, designed to keep working when internet and cellular infrastructure fail.**

Team MACCI · Climate Resilience and Hydrometeorological Disaster Management · September 2026

---

## 1. Background

The Philippines experiences about twenty tropical cyclones a year. Two failures stack during a severe one.

**Resolution failure.** PAGASA and NDRRMC publish accurate information at provincial granularity. A resident deciding whether to drive down a specific street at 4 AM cannot act on a rainfall advisory. The information that matters is street-level, and only the people standing in the water hold it.

**Delivery failure.** The moment that information becomes most valuable — peak of the storm, power out, towers down or congested — is exactly when every cloud-dependent system stops being reachable. A warning system that requires connectivity switches itself off during the emergency.

**This product addresses the delivery failure**, and produces the street-level layer as a consequence of doing so. It does not translate national weather data into local protocols, it does not forecast flooding, and it does not replace a sensor network.

---

## 2. Product Vision

A flood map and rescue channel a barangay keeps using after the network is gone.

The phone holds its own copy of the data and computes its own answers. The server aggregates and accelerates; it does not authorise. In a normal application the phone asks the server what is true — here the phone already knows, and the server exists to help phones learn about each other.

**The governing principle:** capability degrades in fidelity rather than switching off. Full data connection, then SMS, then phone-to-phone relay, then a device that still holds a usable map and can still call for help. Any feature that cannot degrade to something usable offline is not complete.

---

## 3. Governance & Stakeholders

| Stakeholder | Interest | Authority in the system |
|---|---|---|
| **Resident** | Street-level passability; reaching help | Reports and confirms; calls for rescue. No privileged read |
| **Barangay** (kagawad, tanod) | Local accountability and response | Issues official status; activates volunteers; marks requests unfounded |
| **LGU / MDRRMO** | Municipal coordination and resourcing | Dashboard access; issues and revokes credentials; assigns resources |
| **PAGASA / NDRRMC** | Authoritative national advisories | Source only. Advisories are relayed verbatim, never derived from or blended with community data |
| **Volunteer responder** | Turning out effectively | Acknowledges rescue requests. Registered by a barangay, never self-declared |

**Custody and control.** The LGU is the data controller under RA 10173 and holds the signing keys distributed in the regional map pack. Barangay officials hold delegated credentials with a validity window.

**No privileged role is self-granted.** A role that can read a neighbour's exact address cannot be obtained by filling in a form during a flood, when the incentive to claim it dishonestly is highest and the ability to check anyone is lowest.

**Escalation.** Any official may reverse another's official status; both actions are retained and attributable. Lowering the severity of a location in the conflicting state requires a second official.

---

## 4. Scope

**Five core features**, delivered on the native mobile application, none optional:

| # | Feature | Intent |
|---|---|---|
| 1 | Interactive Flood Map | Which roads are flooded, how badly, how recently, how confidently |
| 2 | Crowdsourced Flood Reporting | Report conditions in seconds; let neighbours verify |
| 3 | Real-Time Notifications | Warn about flooding near home or route without opening the app |
| 4 | SOS / Rescue Request | Call for help, and know it was received, with no signal |
| 5 | Offline-First Design | Keep every capability above working through an outage |

**Four additional features**, built only after all five above are fully working:

| # | Feature | Intent |
|---|---|---|
| 6 | Official Verification | Let an accountable office settle what the crowd cannot |
| 7 | Evacuation Centre Directory | Tell people where to go, not only where not to go |
| 8 | Family & Household Check-In | Give the first thing people do a channel that does not congest |
| 9 | Offline Route Check | How to get from here to there without drowning |

**A tenth deliverable**, the LGU dashboard, is a coordination surface rather than an application feature. Built last; everything above must work without it.

### 4.1 Out of scope

This product does not forecast. No rainfall-to-flood model, no river-level prediction, no sensor integration, no projection of where flooding will appear next. Every condition displayed was observed and reported by a person who was there.

Its contribution to early warning is **distribution, not prediction**: carrying existing PAGASA and NDRRMC advisories to people whose connection has already failed. Forecasting belongs to the national agencies whose feeds this product relays; adding it would be a change of scope, not an enhancement.

Also excluded: iOS, a resident web application, and any dependency on a hosted backend service.

---

## 5. Target Users

| Role | Who they are | What they need | What they may see |
|---|---|---|---|
| **Resident** | Parent, commuter, small-business owner. Mid-range Android, prepaid load, rations data and battery | Street passability; alerts near home and route; reassurance about family | Public map, confidence, advisories |
| **Responder / volunteer** | The neighbour with a boat or motorcycle. Not barangay personnel | Triage-ordered requests with location and party size; ability to acknowledge | Adds exact location, party composition. **Never medical detail** |
| **Barangay official** | Kagawad or tanod. Holds office, out in the same water | Everything a responder needs, plus the authority to assert | Adds medical context and full audit |
| **DRRMO / LGU staff** | Municipal officer at a desk with mains power | Whole-municipality view; resource assignment; after-event record | Everything, via the dashboard |

**Constraints shared by the first three:** wet hands or gloves, screen glare or darkness, high stress, frequently no connectivity, and no ability to stop and read a dense screen.

---

## 6. Design Language & Crisis UX

**Three operating modes**, switched by conditions rather than preference:

| Mode | Ground | Purpose |
|---|---|---|
| **Normal** | `#FFFFFF` / canvas `#F7F5F2` | Calm-weather use; full feature set |
| **Storm** | `#0D0F12` | Night, rain, glare; reduced motion; larger targets |
| **Survival** | True black | Critical battery. Map and SOS only; other features visibly suspended, not hidden |

**Severity is constant across all three modes**, because a colour that means "impassable" cannot change meaning with the theme: S1 `#F2A93B` passable with caution, S2 `#E4682B` impassable for cars, S3 `#C42B2B` impassable for all, S0 `#2F7FBF` cleared, SX hatch for conflicting.

**Two deliberate exceptions to mode theming.** SOS chrome is always urgent-styled regardless of mode, and the rescue card stays white regardless of battery state — a card a stranger must read in the dark is worth the power.

**Crisis UX rules.** Severity is expressed as a decision, not a measurement: "impassable for cars" rather than a depth in centimetres. Reporting uses a body-and-vehicle scale — ankle, knee, waist, chest — because coarse readings are what untrained people produce reliably, and no typing is required at any point in the core paths. Every destructive or irreversible action is reachable one-handed with a thumb.

---

## 7. Core Features

### 7.1 Interactive Flood Map

*As a resident, I want to see which specific roads are flooded, so I can decide whether to travel.*

- **FR-1.1** Render flood conditions on roads and areas from the local event store, with no network dependency.
- **FR-1.2** Encode severity by the constant five-state scale, and staleness by visual treatment.
- **FR-1.3** Display a confidence indicator derived from a **weighted corroboration score**, not a raw count. Relay attestation is the strong signal, because a report that reached nearby devices over short-range radio proves the author was within range of them; device-asserted position is weak, because a device can assert any position.
- **FR-1.4** Render contradictory reports as an explicit conflicting state, never as an average or a winner.
- **FR-1.5** State on the map that guidance is assembled from resident reports, may be incomplete or out of date, and that the user remains responsible for judging conditions in front of them.

**AC-1** The map opens, pans and renders every severity state with the device in airplane mode.

### 7.2 Crowdsourced Flood Reporting

*As a resident standing in water, I want to report it in seconds without typing.*

- **FR-2.1** Submit a report by selecting a location and a depth on the body or vehicle scale; derive severity from depth automatically.
- **FR-2.2** Write the report to device storage and display it on the author's own map before attempting any transmission.
- **FR-2.3** Attach an optional photo captured in the application at the time of reporting; the device photo library is not offered as a source.
- **FR-2.4** Carry the existence and content hash of a photo in the report event itself, queueing the image separately at the lowest priority, so every device computes the same confidence whether or not the image has arrived.
- **FR-2.5** Present a report without a photo as less corroborated rather than as doubtful, and never require a photo to submit.
- **FR-2.6** Record two independent presence signals with each event: asserted position, and relay attestation — how many nearby devices received it directly over short-range radio.
- **FR-2.7** Count corroboration only from distinct devices, weighting a confirmation by relay attestation rather than by asserted position.
- **FR-2.8** Embed the author's display name in the event at creation, so it renders on a receiving device with no lookup. It is excluded from the confidence calculation.

**AC-2** A submitted report appears on the author's map before transmission, and reaches a second device over the relay with both devices offline.

### 7.3 Real-Time Notifications

*As a resident, I want to be told about flooding near my home or on my route without opening the app.*

- **FR-3.1** Evaluate geofences locally on every event insert, so alerts fire with no push server and no connectivity.
- **FR-3.2** Support a home radius and saved routes as alert scopes.
- **FR-3.3** Relay PAGASA and NDRRMC advisories verbatim, presented alongside but visually distinct from community reports.
- **FR-3.4** Escalate a rescue request in range to a critical alert that overrides silent mode.

**AC-3** An alert fires on a device in airplane mode when a matching event arrives over the relay.

### 7.4 SOS / Rescue Request

*As someone trapped, I want to call for help and know it was received, with no signal.*

- **FR-4.1** Raise a rescue request from a long-press, carrying location, party size, composition and water level.
- **FR-4.2** Structure the payload in three cryptographically separated parts: a cleartext routing header, a rescue body readable by responders and officials, and a **medical envelope readable by officials alone**.
- **FR-4.3** Broadcast over every available transport, and return the responder's acknowledgement down the same chain.
- **FR-4.4** Never transmit photos by SMS or relay.
- **FR-4.5** When no peer is reachable, enter the rescue-card state: beacon, alarm tone, screen flash, and a scannable card.
- **FR-4.6** Allow an acknowledging responder or any barangay official to mark a request unfounded on arrival, as a signed attributable event; a responder mark is advisory, an official mark authoritative.
- **FR-4.7** Lower the routing priority of later requests from a device with unfounded marks against it, **without ever suppressing them**, restoring priority on successful appeal to the barangay.

**AC-4** A request raised on an offline device reaches a second offline device, and the acknowledgement returns to the sender.

### 7.5 Offline-First Design

- **FR-5.1** Open and operate every core path from pre-downloaded map tiles and the local event store.
- **FR-5.2** Queue all outbound events with a priority class, and offer them to every transport as it becomes available.
- **FR-5.3** Deduplicate by content hash on arrival, so re-delivery over multiple transports is harmless.
- **FR-5.4** Carry forward: relay and later upload events the device did not author.
- **FR-5.5** Display transport state honestly — what has been sent, what is queued, and what is stored only on this phone.

**AC-5** Every acceptance criterion above is demonstrated with the device in airplane mode.

### 7.6–7.9 Additional features

**Built only when all of 7.1–7.5 pass on real hardware in airplane mode.**

| Feature | Key requirements |
|---|---|
| **7.6 Official Verification** | Issue official status from a phone in the field with no connectivity; sign it against the LGU keys in the regional pack so it verifies offline; allow any official to reverse another's, retaining both; require a second official before a clearance lowers a conflicting location's severity |
| **7.7 Evacuation Centre Directory** | Bundled offline directory with capacity and activation state; capacity updates propagate by every transport; render distance and route warnings from local data |
| **7.8 Family & Household Check-In** | A household circle joined by local exchange; "I am safe" as a single-tap event that travels the relay; no server, and no message body to congest the network |
| **7.9 Offline Route Check** | Evaluate a route against local flood state and mark impassable segments; suggest an alternative from the offline graph; state plainly when it cannot find one |

### 7.10 LGU Dashboard

A responsive web console, built last. Authenticated accounts scoped to one LGU with an audit log; live area-wide map using the same severity, confidence and staleness encoding; official verification at scale; rescue queue with grouped incidents; evacuation capacity management; post-event export; and an explicit degraded state when the server is unreachable. Nothing in the mobile application depends on it.

---

## 8. Accessibility & Inclusion

- **Language.** Filipino first, English secondary, on every core screen. Regional languages are a post-launch commitment, not a hackathon one.
- **Literacy.** Every core action is reachable by icon and body-scale illustration without reading prose. Depth is chosen from pictures of a person and a vehicle.
- **Vision.** Minimum 4.5:1 contrast on all text and 3:1 on severity fills, in all three modes. Layout must hold at maximum system font scale.
- **Motor.** One-handed reach for map, report and SOS. Minimum 48 dp targets, raised in Storm mode. No gesture is the only route to any action.
- **Device inclusion.** Android 8 and above on budget handsets. Feature phones that cannot run the application are reached through the SMS path.
- **Colour independence.** Severity is carried by shape and label as well as colour; the conflicting state is a hatch, not a hue.

---

## 9. Privacy & Consent

**Minimum collection.** Residents provide a name and home barangay once, at first run. No email, no password, no address, no contacts. Volunteers additionally provide a mobile number and what they can bring.

**Registration is identification, not authentication.** Nothing entered is verified against anything, because there is nothing offline to verify against. It exists for **attribution** — putting an accountable name on a report so that filing a false one has a social cost. It is never used for access control, permissions, or confidence weighting. If a name could raise confidence, typing one would be a free way to raise it.

**Name visibility.** A report or rescue request displays its author's name to any user who opens it. The displayed form is a first name and last initial with the barangay, never a full legal name or a doorstep.

**Tiered disclosure of rescue requests** is enforced cryptographically, not by interface convention: routing header in cleartext, rescue body to responder and official keys, medical envelope to official keys alone. A volunteer never receives medical context, because a role obtainable by registration should not carry the most sensitive field in the system.

**Consent is revocable within the limits of a replicated store.** A withdrawal is itself an event and removes the item wherever it reaches. **Copies already carried to other devices cannot be recalled**, because there is no authority that can reach into a stranger's phone — and that same property is what makes the product work without a network. This limit is disclosed at the point of collection rather than in a policy, so no one is told their data was erased when it was withdrawn.

**Regulatory position.** The LGU is the data controller under RA 10173. A privacy impact assessment and legal review are prerequisites before any real deployment, and are scheduled in Phase 3.

---

## 10. Data Sources

| Source | Provides | Trust treatment |
|---|---|---|
| **Resident reports** | Street-level flood conditions | Weighted by relay attestation and distinct-device corroboration; expires with staleness |
| **Official verification** | Authoritative local status | Signature-checked offline against LGU keys; overrides crowd display, with the crowd layer retained |
| **PAGASA / NDRRMC** | National advisories and warnings | Relayed verbatim; never blended with or derived into community data |
| **OpenStreetMap** | Base map and offline vector tiles | Pre-downloaded per region |
| **Barangay boundary set** | Offline resolution of GPS to barangay | Bundled with the regional pack |
| **LGU evacuation directory** | Centres, capacity, activation | Provided by the LGU; updated by officials in the field |

---

## 11. Architecture

**Event-sourced and local-first.** An immutable append-only event store feeds a deterministic reducer, which produces the displayed state. Every device holds its own store and computes its own map.

**The local database is a replica, not a cache.** It is not a holding pen flushed into the server and emptied. Two devices holding the same events display the same status, which is what makes an unsynchronised device trustworthy rather than merely stale.

**Three transports**, offered in order by a transport manager:

1. **Server sync** — batch POST, idempotent on event ID; pull by bounding box and cursor. Carries photos.
2. **Device-to-device relay** — Bluetooth and Wi-Fi Direct. Devices exchange event-ID lists and transfer the difference. No server involved.
3. **SMS** — a bit-packed encoding for the cellular-but-no-data case, which also reaches feature phones.

**Stack.** Kotlin and Jetpack Compose, min SDK 26; MapLibre with pre-downloaded offline tiles; Room over SQLite; Nearby Connections for the relay; `SmsManager` for the SMS path. Server: Node and Express with the built-in `node:sqlite` module — Express is the only dependency. Dashboard: one HTML page with MapLibre GL JS. FCM is an optional sync-wake optimisation; every alert fires without it.

**What is lost with no server:** reach beyond relay range, the dashboard, official feed ingestion, SMS gateway bridging. **What survives:** the map, reporting, confirm and dispute, local notifications, and SOS to nearby phones.

---

## 12. Data Flow

**Creating an event.** The user acts. The event is signed and written to the local store. The reducer recomputes, so the author sees the result immediately. The event enters the outbound queue with a priority class, and the transport manager offers it to every available transport.

**Receiving an event.** Devices discard duplicates by content hash, store what is new, recompute their own state, and evaluate their own geofences — which is why notifications fire with no push server.

**The fully offline path.** A report commits locally and appears on the author's map. No data connection and no SMS exist, so the transport manager hands it to the relay. A neighbour's phone receives it, renders it, and fires that neighbour's local alert. The warning has done useful work with no infrastructure whatsoever.

**Carry-forward is the bridge.** A device that picked up events over the relay uploads *all* of them on reconnect, including ones it never authored. That is how a report from a phone that never had signal reaches the server — someone else carries it out.

---

## 13. Non-Functional Requirements

| ID | Requirement |
|---|---|
| **NFR-1** | Every core path functions with no network connection; offline is the default assumption, not a degraded mode |
| **NFR-2** | Cold start to an interactive map in under 3 seconds on a min-spec device |
| **NFR-3** | Battery cost of relay and background evaluation is bounded and disclosed; Survival mode suspends non-essential work |
| **NFR-4** | The reducer is deterministic: two devices with the same events display the same status |
| **NFR-5** | The event store is bounded on device — events past retention are compacted into the state they produce, and compaction is itself deterministic |
| **NFR-6** | An SMS-encoded report fits one 160-character message |
| **NFR-7** | Official actions verify offline against keys distributed in the regional pack, never against a server session |
| **NFR-8** | Layout holds at maximum system font scale, in all three modes |
| **NFR-9** | Transport state is always visible and honest; the application never implies delivery it cannot confirm |

---

## 14. Testing Approach

- **Airplane-mode test, daily.** The claim the product is named for is verified every build day, not at the end. A build that has not been opened in airplane mode is unverified.
- **Two-device determinism test.** Feed the same event set to two devices in different orders; the displayed state must match. This is the check that protects NFR-4 and it is the one worth automating.
- **Three-device relay test.** A and C out of range, B between them. Verifies multi-hop carry rather than a single pairing.
- **Seeded fixtures from day one.** An empty map demos terribly and debugs worse.
- **Tier enforcement test.** A responder credential must fail to decrypt a medical envelope. This is a privacy claim, so it is tested rather than asserted.
- **Manual accessibility pass** at maximum font scale in all three modes.
- **Named gap:** no field measurement of relay range, delivery rate or battery cost in a real barangay, and no OEM device matrix. Both are prerequisites before real deployment and are stated as such rather than implied to be done.

---

## 15. Success Metrics

| Metric | Target |
|---|---|
| Time to file a report | Under 20 seconds, no typing |
| Core paths usable offline | 100% — any failure is a defect, not a degradation |
| Relay delivery, two devices in range | Report visible on the second device within 30 seconds |
| Acknowledgement round trip, both offline | Under 60 seconds across one hop |
| Determinism | Zero divergence between devices holding identical event sets |
| Conflicting state | Rendered as conflicting in 100% of contradiction cases; never averaged |
| Medical tier | Zero decryptions by a responder-tier credential |

---

## 16. Assumptions & Dependencies

**Assumptions**

- Enough neighbours run the application for the relay to find a peer. This is the single largest risk to the premise, and it is unmeasured.
- Residents will report honestly in the ordinary case, and attribution plus weighted corroboration is sufficient against the rest.
- A barangay can register volunteers before or between events, rather than during one.
- Phones retain charge long enough to matter; Survival mode extends this but does not solve it.

**Dependencies**

- Google Play Services for Nearby Connections and fused location.
- Runtime grants for location, SMS and nearby devices — available because the build is sideloaded rather than Play-distributed.
- LGU cooperation for key issuance, the evacuation directory, and dashboard operation.
- PAGASA and NDRRMC feeds remaining publicly retrievable.
- Device storage for regional tile packs.

**Two limits stated rather than designed around.** A coordinated group physically present in a barangay can raise confidence on a false report, because presence is the strongest signal available and they have it. And a report authored offline reaches neighbouring phones before any server sees it, so no server-side filter can act before local spread. Both are consequences of an offline-first design, and both are accepted.

---

## 17. Implementation Phases

| Phase | Contents | Exit condition |
|---|---|---|
| **1 — Offline core** | Offline map and tiles; reporting with derived severity; confirm and dispute; the reducer with conflict and staleness rules; local notifications | Every path demonstrated in airplane mode on real hardware |
| **2 — Relay and SOS** | Nearby Connections relay with carry-forward; SOS with three-part payload; responder acknowledgement; rescue-card state; unfounded marking; credentials with a validity window | An SOS raised offline reaches a second offline device and the acknowledgement returns |
| **3 — Trust and inclusion** | Official verification; volunteer registration and barangay activation; evacuation directory; SMS path; **legal review and RA 10173 privacy impact assessment** | An official status issued offline propagates by relay and verifies on a device that has never been online |
| **4 — Reach** | Family check-in; offline route check; fixed relay nodes at barangay halls; field measurement of relay range and battery cost | Measured relay performance supports or refutes the core premise, and the product is adjusted accordingly |
| **5 — LGU dashboard** | Authenticated LGU accounts with audit log; area-wide map; verification at scale; rescue queue; capacity management; export; degraded state | An officer verifies a report and it reaches a resident device — with the five core features fully operable with the dashboard switched off |

---

*Companion documents: `PROJECT_CONCEPT.md` (full architecture), `BUILD_PLAN.md` (schedule and risk register), `ROUTING.md` (prototype routing matrix), and the 29-artboard hi-fi design canvas.*
