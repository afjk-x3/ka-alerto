<!--
Living copy of the PRD, kept in the repo (unlike docs/, which is gitignored) so it can be
updated alongside the code as development proceeds. Synced with docs/02-prd.md and
checked against the built app and dashboard on 24 Sep 2026 (*As built* notes).

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

**Custody and control.** The LGU is the data controller under RA 10173 and holds the signing keys distributed in the regional map pack. Barangay officials hold delegated credentials with a validity window. *As built: this submission contains no cryptography, so there are no keys or credentials; official actions are attributed by name and role, not signed.*

**No privileged role is self-granted.** A role that can read a neighbour's exact address cannot be obtained by filling in a form during a flood, when the incentive to claim it dishonestly is highest and the ability to check anyone is lowest. *As built: the apply-and-activate flow is written but switched off for the demo, which uses a manual role picker so roles can be tested on a handful of phones.*

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

Also excluded: iOS, a resident web application, and any dependency on a hosted backend service. *As built: Supabase is used for cloud sync and the dashboard, but no core path depends on it; every feature above works without it (§11).*

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
| **Survival** | True black | Critical battery. Map and SOS only; other features visibly suspended, not hidden. *As built: turns on by itself at 15% battery when not charging, and can be switched on or off from the menu; periodic cloud sync pauses, while new events still upload at once and the Bluetooth relay stays on* |

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
- **FR-1.5** State on the map that guidance is assembled from resident reports, may be incomplete or out of date, and that the user remains responsible for judging conditions in front of them. *As built: the statement sits in the map's colour key ("Kahulugan"), one tap from the map, rather than in a permanent strip that cost a row of map height.*
- **FR-1.6** On request, and only with a connection, show routes from the resident's position to a reported spot or a rescue request, drawn on the map and ranked by how many current reported floods each passes. State that the ranking reflects reports only: a road with no report is not known to be safe. With no connection, say so and hand the destination to another navigation application; never draw an unverified route.

**AC-1** The map opens, pans and renders every severity state with the device in airplane mode. A route request in airplane mode says routes need the internet and draws nothing.

### 7.2 Crowdsourced Flood Reporting

*As a resident standing in water, I want to report it in seconds without typing.*

- **FR-2.1** Submit a report by selecting a location and a depth on the body or vehicle scale; derive severity from depth automatically.
- **FR-2.2** Write the report to device storage and display it on the author's own map before attempting any transmission.
- **FR-2.3** Attach an optional photo captured in the application at the time of reporting; the device photo library is not offered as a source.
- **FR-2.4** Carry the existence and content hash of a photo in the report event itself, queueing the image separately at the lowest priority, so every device computes the same confidence whether or not the image has arrived. The image itself uploads and downloads by hash through Supabase only, best-effort, when a connection is available; it never travels over the device-to-device relay (FR-4.4).
- **FR-2.5** Present a report without a photo as less corroborated rather than as doubtful, and never require a photo to submit.
- **FR-2.6** Record two independent presence signals with each event: asserted position, and relay attestation — how many nearby devices received it directly over short-range radio.
- **FR-2.7** Count corroboration only from distinct devices, weighting a confirmation by relay attestation rather than by asserted position.
- **FR-2.8** Embed the author's display name in the event at creation, so it renders on a receiving device with no lookup. It is excluded from the confidence calculation.
- **FR-2.9** Let an author withdraw their own say on a spot. A withdrawal is a new event, never a deletion: it cancels that author's earlier reports, confirmations and disputes on that feature, and nobody else's. Every device folds it identically, whatever order events arrive in. A spot with nothing left is removed from the map rather than shown as clear, and the withdrawal itself stays visible in the spot's history. It travels by the same transports as the report it cancels and lives at least as long as the events it cancels.

**AC-2** A submitted report appears on the author's map before transmission, and reaches a second device over the relay with both devices offline. A withdrawn report disappears from both maps.

### 7.3 Real-Time Notifications

*As a resident, I want to be told about flooding near my home or on my route without opening the app.*

- **FR-3.1** Evaluate geofences locally on every event insert, so alerts fire with no push server and no connectivity.
- **FR-3.2** Support a home radius and saved routes as alert scopes. *As built: up to three routes saved from the route panel, kept on the phone only; a new report within 50 m of one raises an alert.*
- **FR-3.3** Relay PAGASA and NDRRMC advisories verbatim, presented alongside but visually distinct from community reports. *As built: PAGASA only — its public CAP alert feed; NDRRMC publishes no feed. A phone with internet stores each alert covering its home province word for word, under PAGASA's own id, and it relays like any event. It shows as a PAGASA strip on the map and a notification.*
- **FR-3.4** Escalate a rescue request in range to a critical alert that overrides silent mode.
- **FR-3.5** Detect a genuinely slow or failing sync connection from real, observed sync attempts — never an inferred signal-strength estimate — and prompt the resident to enable the device-to-device relay as a backup.
- **FR-3.6** When a fresh report lands inside a resident's home radius, ask them to confirm it, and open that report's detail on tap. Do not prompt the report's author, or anyone who has already confirmed or disputed that spot. A prompt never adds corroboration by itself: a confirmation still weighs by what the reducer measures when it is made (FR-2.6, FR-2.7).

**AC-3** An alert fires on a device in airplane mode when a matching event arrives over the relay.

### 7.4 SOS / Rescue Request

*As someone trapped, I want to call for help and know it was received, with no signal.*

- **FR-4.1** Raise a rescue request from a long-press, carrying location, party size, composition and water level. The request goes out at once even without a location fix: it is sent as "location unknown", never a guessed point, and filled in afterwards, best source first — the requester's own GPS when it finds a fix (one tap turns location on), a spot the requester taps on the offline map, or, until either arrives, the position of a phone that heard the request directly over Bluetooth, shown as approximate. The requester's home barangay travels with the request as a hint and is never shown as a location.
- **FR-4.2** Structure the payload in three cryptographically separated parts: a cleartext routing header, a rescue body readable by responders and officials, and a **medical envelope readable by officials alone**. *As built: with no cryptography, the medical detail is removed instead of encrypted — it never leaves the requester's phone, so no official receives it either — and the requester's name is removed from every copy that leaves the phone.*
- **FR-4.3** Broadcast over every available transport, and return the responder's acknowledgement down the same chain.
- **FR-4.4** Never transmit photos by SMS or relay.
- **FR-4.5** When no peer is reachable, enter the rescue-card state: beacon, alarm tone, screen flash, and a scannable card. *As built: all four; the flash strobes the screen and the camera flashlight together at two flashes a second, below the photosensitive-seizure threshold.*
- **FR-4.6** Allow an acknowledging responder or any barangay official to mark a request unfounded on arrival, as a signed attributable event; a responder mark is advisory, an official mark authoritative. *As built: only an official can mark a request, the mark is attributed but not signed, and an official can undo it.*
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
| **7.6 Official Verification** | Issue official status from a phone in the field with no connectivity; sign it against the LGU keys in the regional pack so it verifies offline; allow any official to reverse another's, retaining both; require a second official before a clearance lowers a conflicting location's severity. *As built: offline posting, reversal and the second-official gate work; statuses are attributed, not signed* |
| **7.7 Evacuation Centre Directory** | Bundled offline directory with capacity and activation state (accepting, nearly full, full, not open yet); officials add, open, close and remove shelters for their own municipality and type the head count, and every device folds the same events into the same list; changes propagate by every transport; residents see every centre nearest first, filterable by province and town; render distance and route warnings from local data. *As built: 18 bundled centres, 13 of them evacuation centres mapped in OpenStreetMap across Pangasinan, with no capacity figure; none are mapped inside Mapandan, whose official list is still to come. Each shelter shows a QR code that points to a placeholder link.* |
| **7.8 Family & Household Check-In** | A household circle, created once and joined by an 8-digit code that can be read out over a call, a QR code, or a shared message; "I am safe" as a single-tap event that travels the relay; no message body to congest the network. *As built: circle membership and names sync through the project's backend so a code works from anywhere; check-ins travel the relay only, in the clear.* |
| **7.9 Offline Route Check** | Evaluate a route against local flood state and mark impassable segments; suggest an alternative from the offline graph; state plainly when it cannot find one. *As built: online, routes come from a public routing service (FR-1.6); with no internet, the phone searches a bundled road graph of Pangasinan built from OpenStreetMap, avoids roads within 75 m of spots reported impassable or conflicting, and says so when it can find no clear route* |

### 7.10 LGU Dashboard

A responsive web console, built last. Authenticated accounts scoped to one LGU with an audit log; live area-wide map using the same severity, confidence and staleness encoding; official verification at scale; rescue queue with grouped incidents; evacuation capacity management; post-event export; and an explicit degraded state when the server is unreachable. Nothing in the mobile application depends on it.

**As built for the demo:** a shared-PIN gate (not personal accounts, no audit log); a live area-wide map and two lists refreshed every 5 seconds: SOS requests, and flooded spots, each folded from its reports by the same reducer the phones use, so a spot shows the status, confidence and official ruling a phone would show for the same events; sound and browser-notification alerts for SOS requests and for a spot reaching S3, which keep working while the tab is in the background; report photos; for each SOS, where its location came from (GPS, a spot picked on the map, a relaying phone's position, or only the requester's home barangay); directions to a spot or SOS from a start the operator chooses (this computer, a saved station, or a click on the map), with alternative routes ranked by nearby flooded spots; filters by age, severity and SOS state; CSV export of the filtered list; and a shelters tab for one municipality at a time, chosen by the operator, showing each of its evacuation centres with a map pin and letting the operator add, open, close and remove them, mark them nearly full or full, and record the head count. Not built: LGU-scoped accounts, the audit log, grouped incidents, and official verification at scale. Shelter changes made here are checked only by the shared PIN, not by an official's seat, and are attributed to "Dashboard".

---

## 8. Accessibility & Inclusion

- **Language.** Filipino first, English secondary, on every core screen. Regional languages are a post-launch commitment, not a hackathon one.
- **Literacy.** Every core action is reachable by icon and body-scale illustration without reading prose. Depth is chosen from pictures of a person and a vehicle.
- **Vision.** Minimum 4.5:1 contrast on all text and 3:1 on severity fills, in all three modes. Layout must hold at maximum system font scale.
- **Motor.** One-handed reach for map, report and SOS. Minimum 48 dp targets, raised in Storm mode. No gesture is the only route to any action.
- **Device inclusion.** Android 8 and above on budget handsets. Feature phones that cannot run the application are reached through the SMS path. *As built: the SMS path is not built (§11), so feature phones are not reached.*
- **Colour independence.** Severity is carried by shape and label as well as colour; the conflicting state is a hatch, not a hue.

---

## 9. Privacy & Consent

**Minimum collection.** Residents provide a name and home barangay once, at first run, and may add their municipality. When a location fix is available the municipality and barangay are filled in from it and remain the resident's to correct. No email, no password, no address, no contacts. Volunteers additionally provide a mobile number and what they can bring.

**Registration is identification, not authentication.** Nothing entered is verified against anything, because there is nothing offline to verify against. It exists for **attribution** — putting an accountable name on a report so that filing a false one has a social cost. It is never used for access control, permissions, or confidence weighting. If a name could raise confidence, typing one would be a free way to raise it.

**Name visibility.** A report or rescue request displays its author's name to any user who opens it. The displayed form is a first name and last initial with the barangay, never a full legal name or a doorstep.

**Routes are the one place a position leaves the device for a third party.** Requesting a route sends the device's current position and the destination to a public routing service; nothing else in the application does this, since events go only to the project's own backend, with rescue detail redacted. It is sent only when the resident asks, after a one-time disclosure, and carries no name or identifier. Self-hosting the routing service removes the third party. Offline routes, searched on the bundled road graph, send nothing. Fetching PAGASA's advisory feed sends no position or identity either — only an ordinary request to PAGASA's public server.

**What a rescue request carries besides its location.** Every request carries the requester's home barangay and town, so responders have somewhere to start when there is no fix; it travels without the requester's name, over the relay and to the project's backend. A phone that receives a request with no location directly over Bluetooth attaches its own position to it, also without a name, so that phone's approximate whereabouts leave it too. Both are stated here because neither asks the person each time.

**Municipality detection uses the device's own geocoder.** To fill in the municipality, the device asks the platform's location service, which is typically a network call to the platform vendor, and matches the answer against a Philippine Standard Geographic Code list bundled in the application. The list itself works offline and sends nothing anywhere; without a connection the fields are left for the resident to fill in.

**Tiered disclosure of rescue requests** is enforced cryptographically, not by interface convention: routing header in cleartext, rescue body to responder and official keys, medical envelope to official keys alone. A volunteer never receives medical context, because a role obtainable by registration should not carry the most sensitive field in the system. *As built: not cryptographic — see FR-4.2; medical detail stays on the requester's phone.*

**Consent is revocable within the limits of a replicated store.** A withdrawal is itself an event and removes the item wherever it reaches. **Copies already carried to other devices cannot be recalled**, because there is no authority that can reach into a stranger's phone — and that same property is what makes the product work without a network. This limit is disclosed at the point of collection rather than in a policy, so no one is told their data was erased when it was withdrawn.

**Regulatory position.** The LGU is the data controller under RA 10173. A privacy impact assessment and legal review are prerequisites before any real deployment, and are scheduled in Phase 3.

---

## 10. Data Sources

| Source | Provides | Trust treatment |
|---|---|---|
| **Resident reports** | Street-level flood conditions | Weighted by relay attestation and distinct-device corroboration; expires with staleness |
| **Official verification** | Authoritative local status | Signature-checked offline against LGU keys; overrides crowd display, with the crowd layer retained. *As built: attributed, not signed* |
| **PAGASA / NDRRMC** | National advisories and warnings | Relayed verbatim; never blended with or derived into community data. *As built: PAGASA's public CAP feed (CC BY 4.0); NDRRMC has no feed* |
| **OpenStreetMap** | Base map and offline vector tiles | Pre-downloaded per region. *As built: OpenFreeMap tiles, pre-downloaded for the whole of Pangasinan* |
| **Barangay boundary set** | Offline resolution of GPS to barangay | Bundled with the regional pack. *As built: a bundled PSGC list of municipality and barangay names, matched to the device geocoder's answer; no boundary shapes* |
| **LGU evacuation directory** | Centres, capacity, activation | Provided by the LGU; updated by officials in the field. *As built: centres mapped in OpenStreetMap plus officials' additions; the LGU list for Mapandan is still to come* |

---

## 11. Architecture

**Event-sourced and local-first.** An immutable append-only event store feeds a deterministic reducer, which produces the displayed state. Every device holds its own store and computes its own map.

**The local database is a replica, not a cache.** It is not a holding pen flushed into the server and emptied. Two devices holding the same events display the same status, which is what makes an unsynchronised device trustworthy rather than merely stale.

**Three transports**, offered in order by a transport manager:

1. **Cloud sync** — batch upsert to Supabase, idempotent on event ID; pull everything, no cursor and no location filter. Photos travel by hash, best-effort. A background job repeats the upload when the app is closed. Between full pulls a row count is checked every 5 seconds, and a change triggers the pull at once, so a new rescue request reaches other phones in seconds rather than at the next 30-second cycle.
2. **Device-to-device relay** — Bluetooth only (Nearby Connections in low-power mode; the Wi-Fi mediums were dropped because they switched the phone's Wi-Fi on). Devices exchange event-ID lists and transfer the difference. No server involved.
3. **SMS** — a bit-packed encoding for the cellular-but-no-data case, which also reaches feature phones. *Status: designed, not built in this submission; sending SMS is charged, so the app carries only a placeholder.*

**Stack.** Kotlin and Jetpack Compose, min SDK 26; MapLibre with pre-downloaded offline tiles; Room over SQLite; Nearby Connections for the relay; `SmsManager` for the SMS path (placeholder only). Backend: Supabase (Postgres and Storage), baked into the app with no address to configure — reachable anywhere with signal, needs the internet to be up. No self-hosted server. Dashboard: a light-mode Next.js web console that reads Supabase behind one shared PIN (demo access, not personal accounts). FCM is an optional sync-wake optimisation; every alert fires without it. *As built: FCM is not used; a background job repeats the upload instead.*

**What is lost with no internet:** reach beyond relay range, cloud sync, the dashboard, official feed ingestion, SMS gateway bridging, and the online router (routes then come from the bundled road graph). **What survives:** the map, reporting, confirm and dispute, local notifications, and SOS to nearby phones.

---

## 12. Data Flow

**Creating an event.** The user acts. The event is signed and written to the local store (*as built: not signed — no cryptography in this submission*). The reducer recomputes, so the author sees the result immediately. The event enters the outbound queue with a priority class, and the transport manager offers it to every available transport.

**Receiving an event.** Devices discard duplicates by content hash, store what is new, recompute their own state, and evaluate their own geofences — which is why notifications fire with no push server.

**The fully offline path.** A report commits locally and appears on the author's map. No data connection and no SMS exist, so the transport manager hands it to the relay. A neighbour's phone receives it, renders it, and fires that neighbour's local alert. The warning has done useful work with no infrastructure whatsoever.

**Carry-forward is the bridge.** A device that picked up events over the relay uploads *all* of them on reconnect, including ones it never authored. That is how a report from a phone that never had signal reaches the server — someone else carries it out.

---

## 13. Non-Functional Requirements

| ID | Requirement |
|---|---|
| **NFR-1** | Every core path functions with no network connection; offline is the default assumption, not a degraded mode |
| **NFR-2** | Cold start to an interactive map in under 3 seconds on a min-spec device |
| **NFR-3** | Battery cost of relay and background evaluation is bounded and disclosed; Survival mode suspends non-essential work. *As built: Survival mode pauses periodic sync and advisory fetching; battery cost is still unmeasured (§14)* |
| **NFR-4** | The reducer is deterministic: two devices with the same events display the same status |
| **NFR-5** | The event store is bounded on device — events past retention are compacted into the state they produce, and compaction is itself deterministic. *As built: expired events are deleted after a 24-hour grace period (kept longer if not yet uploaded), not compacted* |
| **NFR-6** | An SMS-encoded report fits one 160-character message. *SMS not built* |
| **NFR-7** | Official actions verify offline against keys distributed in the regional pack, never against a server session. *Not built: no cryptography in this submission* |
| **NFR-8** | Layout holds at maximum system font scale, in all three modes |
| **NFR-9** | Transport state is always visible and honest; the application never implies delivery it cannot confirm |

---

## 14. Testing Approach

- **Airplane-mode test, daily.** The claim the product is named for is verified every build day, not at the end. A build that has not been opened in airplane mode is unverified.
- **Two-device determinism test.** Feed the same event set to two devices in different orders; the displayed state must match. This is the check that protects NFR-4 and it is the one worth automating.
- **Three-device relay test.** A and C out of range, B between them. Verifies multi-hop carry rather than a single pairing.
- **Seeded fixtures from day one.** An empty map demos terribly and debugs worse.
- **Tier enforcement test.** A responder credential must fail to decrypt a medical envelope. This is a privacy claim, so it is tested rather than asserted. *As built: there is no envelope to decrypt (FR-4.2); unit tests instead check that medical detail and the requester's name are removed from every copy that leaves the phone.*
- **Manual accessibility pass** at maximum font scale in all three modes.
- **Named gap:** no field measurement of relay range, delivery rate or battery cost in a real barangay, and no OEM device matrix. Both are prerequisites before real deployment and are stated as such rather than implied to be done. *Attempted 23 Sep: the range test was blocked (no second device available), the battery run was inconclusive (the phone stayed on USB power), and delivery rate was not attempted.*

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
| Medical tier | Zero decryptions by a responder-tier credential. *As built: met by removal — medical detail never leaves the device* |

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

*Companion material in the repository: `README.md`, `BUILD_LOG.md` (the day-by-day build record, with how each feature was verified), and the 29-artboard hi-fi design canvas in `design/`. The architecture document, build plan and routing matrix are submitted separately.*
