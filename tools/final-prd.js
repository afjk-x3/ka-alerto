const fs = require('fs');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType,
  Table, TableRow, TableCell, WidthType, ShadingType, BorderStyle,
  Header, Footer, PageNumber,
} = require('docx');

const PAGE_W = 12240, PAGE_H = 15840, MARGIN = 1080;
const CW = PAGE_W - MARGIN * 2; // 10080 DXA
const NAVY = '1F3864', ACCENT = 'C0392B', GREY = '595959', LIGHT = 'F2F4F7';

const H1 = (t) => new Paragraph({ text: t, heading: HeadingLevel.HEADING_1, spacing: { before: 280, after: 120 } });
const H2 = (t) => new Paragraph({ text: t, heading: HeadingLevel.HEADING_2, spacing: { before: 200, after: 90 } });
const H3 = (t) => new Paragraph({ text: t, heading: HeadingLevel.HEADING_3, spacing: { before: 160, after: 70 } });

const P = (t, o = {}) => new Paragraph({
  children: [new TextRun({ text: t, italics: !!o.i, bold: !!o.b, color: o.color })],
  spacing: { after: o.after === undefined ? 100 : o.after },
  alignment: o.align,
});

const RP = (parts, o = {}) => new Paragraph({
  children: parts.map(([text, x = {}]) => new TextRun({ text, bold: !!x.b, italics: !!x.i, color: x.color })),
  spacing: { after: o.after === undefined ? 100 : o.after },
});

const B = (t) => new Paragraph({ text: t, bullet: { level: 0 }, spacing: { after: 50 } });

const BB = (lead, rest) => new Paragraph({
  children: [new TextRun({ text: lead, bold: true }), new TextRun({ text: rest })],
  bullet: { level: 0 }, spacing: { after: 50 },
});

const CALLOUT = (t) => new Paragraph({
  children: [new TextRun({ text: t, bold: true, color: NAVY })],
  spacing: { before: 100, after: 130 },
  border: { left: { style: BorderStyle.SINGLE, size: 18, color: ACCENT, space: 10 } },
  indent: { left: 150 },
});

function cell(text, w, o = {}) {
  const paras = (Array.isArray(text) ? text : [text]).map((t) => new Paragraph({
    children: [new TextRun({ text: String(t), bold: !!o.bold, color: o.color, size: 18 })],
    spacing: { before: 30, after: 30 },
  }));
  return new TableCell({
    width: { size: w, type: WidthType.DXA },
    shading: o.fill ? { type: ShadingType.CLEAR, color: 'auto', fill: o.fill } : undefined,
    margins: { top: 50, bottom: 50, left: 100, right: 100 },
    children: paras,
  });
}

function table(headers, rows, widths) {
  return new Table({
    columnWidths: widths,
    width: { size: widths.reduce((a, b) => a + b, 0), type: WidthType.DXA },
    borders: {
      top: { style: BorderStyle.SINGLE, size: 4, color: 'BFBFBF' },
      bottom: { style: BorderStyle.SINGLE, size: 4, color: 'BFBFBF' },
      left: { style: BorderStyle.SINGLE, size: 4, color: 'BFBFBF' },
      right: { style: BorderStyle.SINGLE, size: 4, color: 'BFBFBF' },
      insideHorizontal: { style: BorderStyle.SINGLE, size: 2, color: 'D9D9D9' },
      insideVertical: { style: BorderStyle.SINGLE, size: 2, color: 'D9D9D9' },
    },
    rows: [
      new TableRow({
        tableHeader: true,
        children: headers.map((h, i) => cell(h, widths[i], { bold: true, color: 'FFFFFF', fill: NAVY })),
      }),
      ...rows.map((r, ri) => new TableRow({
        children: r.map((c, i) => cell(c, widths[i], { fill: ri % 2 ? LIGHT : undefined })),
      })),
    ],
  });
}

const SPACER = new Paragraph({ text: '', spacing: { after: 120 } });

// requirement line: bold ID, then text
const FR = (id, text) => new Paragraph({
  children: [new TextRun({ text: id, bold: true, color: NAVY }), new TextRun({ text: '  ' + text })],
  bullet: { level: 0 }, spacing: { after: 50 },
});

const AC = (id, text) => new Paragraph({
  children: [new TextRun({ text: id + '  ', bold: true, color: ACCENT }), new TextRun({ text, italics: true })],
  spacing: { before: 60, after: 140 }, indent: { left: 150 },
});

const STORY = (t) => new Paragraph({
  children: [new TextRun({ text: t, italics: true, color: NAVY, size: 20 })],
  spacing: { after: 100 },
  border: { left: { style: BorderStyle.SINGLE, size: 10, color: NAVY, space: 10 } },
  indent: { left: 150 },
});

// ============================================================
const children = [];

// ---------- title ----------
children.push(
  new Paragraph({ text: '', spacing: { after: 1600 } }),
  new Paragraph({
    children: [new TextRun({ text: 'KAALERTO', bold: true, size: 56, color: NAVY })],
    alignment: AlignmentType.CENTER, spacing: { after: 100 },
  }),
  new Paragraph({
    children: [new TextRun({ text: 'Product Requirements Document', size: 30, color: GREY })],
    alignment: AlignmentType.CENTER, spacing: { after: 340 },
  }),
  new Paragraph({
    children: [new TextRun({
      text: 'A community flood-intelligence and rescue-coordination application for Philippine barangays, designed to keep working when internet and cellular infrastructure fail.',
      size: 22, italics: true, color: GREY,
    })],
    alignment: AlignmentType.CENTER, spacing: { after: 400 },
  }),
  new Paragraph({
    children: [new TextRun({ text: 'Team MACCI', bold: true, size: 22, color: NAVY })],
    alignment: AlignmentType.CENTER, spacing: { after: 60 },
  }),
  new Paragraph({
    children: [new TextRun({ text: 'Climate Resilience and Hydrometeorological Disaster Management  ·  September 2026', size: 19, color: GREY })],
    alignment: AlignmentType.CENTER, spacing: { after: 400 },
  }),
  new Paragraph({ pageBreakBefore: true, text: '' }),
);

// ============ 1. BACKGROUND ============
children.push(
  H1('1. Background'),
  P('The Philippines experiences about twenty tropical cyclones a year. Two failures stack during a severe one.'),
  RP([['Resolution failure. ', { b: true }], ['PAGASA and NDRRMC publish accurate information at provincial granularity. A resident deciding whether to drive down a specific street at 4 AM cannot act on a rainfall advisory. The information that matters is street-level, and only the people standing in the water hold it.']]),
  RP([['Delivery failure. ', { b: true }], ['The moment that information becomes most valuable — peak of the storm, power out, towers down or congested — is exactly when every cloud-dependent system stops being reachable. A warning system that requires connectivity switches itself off during the emergency.']]),
  CALLOUT('This product addresses the delivery failure, and produces the street-level layer as a consequence of doing so. It does not translate national weather data into local protocols, it does not forecast flooding, and it does not replace a sensor network.'),
);

// ============ 2. PRODUCT VISION ============
children.push(
  H1('2. Product Vision'),
  P('A flood map and rescue channel a barangay keeps using after the network is gone.'),
  P('The phone holds its own copy of the data and computes its own answers. The server aggregates and accelerates; it does not authorise. In a normal application the phone asks the server what is true — here the phone already knows, and the server exists to help phones learn about each other.'),
  RP([['The governing principle: ', { b: true }], ['capability degrades in fidelity rather than switching off. Full data connection, then SMS, then phone-to-phone relay, then a device that still holds a usable map and can still call for help. Any feature that cannot degrade to something usable offline is not complete.']]),
);

// ============ 3. GOVERNANCE ============
children.push(
  H1('3. Governance and Stakeholders'),
  table(['Stakeholder', 'Interest', 'Authority in the system'], [
    ['Resident', 'Street-level passability; reaching help', 'Reports and confirms; calls for rescue. No privileged read'],
    ['Barangay (kagawad, tanod)', 'Local accountability and response', 'Issues official status; activates volunteers; marks requests unfounded'],
    ['LGU / MDRRMO', 'Municipal coordination and resourcing', 'Dashboard access; issues and revokes credentials; assigns resources'],
    ['PAGASA / NDRRMC', 'Authoritative national advisories', 'Source only. Advisories are relayed verbatim, never derived from or blended with community data'],
    ['Volunteer responder', 'Turning out effectively', 'Acknowledges rescue requests. Registered by a barangay, never self-declared'],
  ], [1900, 2700, 5480]),
  SPACER,
  BB('Custody and control. ', 'The LGU is the data controller under RA 10173 and holds the signing keys distributed in the regional map pack. Barangay officials hold delegated credentials with a validity window.'),
  BB('No privileged role is self-granted. ', 'A role that can read a neighbour’s exact address cannot be obtained by filling in a form during a flood, when the incentive to claim it dishonestly is highest and the ability to check anyone is lowest.'),
  BB('Escalation. ', 'Any official may reverse another’s official status; both actions are retained and attributable. Lowering the severity of a location in the conflicting state requires a second official.'),
);

// ============ 4. SCOPE ============
children.push(
  H1('4. Scope'),
  RP([['Five core features', { b: true }], [', delivered on the native mobile application, none optional:']]),
  table(['#', 'Feature', 'Intent'], [
    ['1', 'Interactive Flood Map', 'Which roads are flooded, how badly, how recently, how confidently'],
    ['2', 'Crowdsourced Flood Reporting', 'Report conditions in seconds; let neighbours verify'],
    ['3', 'Real-Time Notifications', 'Warn about flooding near home or route without opening the application'],
    ['4', 'SOS / Rescue Request', 'Call for help, and know it was received, with no signal'],
    ['5', 'Offline-First Design', 'Keep every capability above working through an outage'],
  ], [520, 2900, 6660]),
  SPACER,
  RP([['Four additional features', { b: true }], [', built only after all five above are fully working:']]),
  table(['#', 'Feature', 'Intent'], [
    ['6', 'Official Verification', 'Let an accountable office settle what the crowd cannot'],
    ['7', 'Evacuation Centre Directory', 'Tell people where to go, not only where not to go'],
    ['8', 'Family and Household Check-In', 'Give the first thing people do a channel that does not congest'],
    ['9', 'Offline Route Check', 'How to get from here to there without drowning'],
  ], [520, 2900, 6660]),
  SPACER,
  RP([['A tenth deliverable', { b: true }], [', the LGU dashboard, is a coordination surface rather than an application feature. Built last; everything above must work without it.']]),

  H2('4.1 Out of scope'),
  P('This product does not forecast. No rainfall-to-flood model, no river-level prediction, no sensor integration, no projection of where flooding will appear next. Every condition displayed was observed and reported by a person who was there.'),
  RP([['Its contribution to early warning is ', {}], ['distribution, not prediction', { b: true }], [': carrying existing PAGASA and NDRRMC advisories to people whose connection has already failed. Forecasting belongs to the national agencies whose feeds this product relays; adding it would be a change of scope, not an enhancement.']]),
  P('Also excluded: iOS, a resident web application, and any dependency on a hosted backend service.'),
);

// ============ 5. TARGET USERS ============
children.push(
  H1('5. Target Users'),
  table(['Role', 'Who they are', 'What they need', 'What they may see'], [
    ['Resident', 'Parent, commuter, small-business owner. Mid-range Android, prepaid load, rations data and battery', 'Street passability; alerts near home and route; reassurance about family', 'Public map, confidence, advisories'],
    ['Responder / volunteer', 'The neighbour with a boat or motorcycle. Not barangay personnel', 'Triage-ordered requests with location and party size; ability to acknowledge', 'Adds exact location and party composition. Never medical detail'],
    ['Barangay official', 'Kagawad or tanod. Holds office, out in the same water', 'Everything a responder needs, plus the authority to assert', 'Adds medical context and full audit'],
    ['DRRMO / LGU staff', 'Municipal officer at a desk with mains power', 'Whole-municipality view; resource assignment; after-event record', 'Everything, via the dashboard'],
  ], [1500, 2900, 2900, 2780]),
  SPACER,
  RP([['Constraints shared by the first three: ', { b: true }], ['wet hands or gloves, screen glare or darkness, high stress, frequently no connectivity, and no ability to stop and read a dense screen.']]),
);

// ============ 6. DESIGN LANGUAGE ============
children.push(
  H1('6. Design Language and Crisis UX'),
  RP([['Three operating modes', { b: true }], [', switched by conditions rather than preference:']]),
  table(['Mode', 'Ground', 'Purpose'], [
    ['Normal', '#FFFFFF / canvas #F7F5F2', 'Calm-weather use; full feature set'],
    ['Storm', '#0D0F12', 'Night, rain, glare; reduced motion; larger targets'],
    ['Survival', 'True black', 'Critical battery. Map and SOS only; other features visibly suspended, not hidden'],
  ], [1400, 2600, 6080]),
  SPACER,
  RP([['Severity is constant across all three modes', { b: true }], [', because a colour that means “impassable” cannot change meaning with the theme: S1 #F2A93B passable with caution, S2 #E4682B impassable for cars, S3 #C42B2B impassable for all, S0 #2F7FBF cleared, SX hatch for conflicting.']]),
  RP([['Two deliberate exceptions to mode theming. ', { b: true }], ['SOS chrome is always urgent-styled regardless of mode, and the rescue card stays white regardless of battery state — a card a stranger must read in the dark is worth the power.']]),
  RP([['Crisis UX rules. ', { b: true }], ['Severity is expressed as a decision, not a measurement: “impassable for cars” rather than a depth in centimetres. Reporting uses a body-and-vehicle scale — ankle, knee, waist, chest — because coarse readings are what untrained people produce reliably, and no typing is required at any point in the core paths. Every destructive or irreversible action is reachable one-handed with a thumb.']]),
);

// ============ 7. CORE FEATURES ============
children.push(
  H1('7. Core Features'),

  H2('7.1 Interactive Flood Map'),
  STORY('As a resident, I want to see which specific roads are flooded, so I can decide whether to travel.'),
  FR('FR-1.1', 'Render flood conditions on roads and areas from the local event store, with no network dependency.'),
  FR('FR-1.2', 'Encode severity by the constant five-state scale, and staleness by visual treatment.'),
  FR('FR-1.3', 'Display a confidence indicator derived from a weighted corroboration score, not a raw count. Relay attestation is the strong signal, because a report that reached nearby devices over short-range radio proves the author was within range of them; device-asserted position is weak, because a device can assert any position.'),
  FR('FR-1.4', 'Render contradictory reports as an explicit conflicting state, never as an average or a winner.'),
  FR('FR-1.5', 'State on the map that guidance is assembled from resident reports, may be incomplete or out of date, and that the user remains responsible for judging conditions in front of them.'),
  AC('AC-1', 'The map opens, pans and renders every severity state with the device in airplane mode.'),

  H2('7.2 Crowdsourced Flood Reporting'),
  STORY('As a resident standing in water, I want to report it in seconds without typing.'),
  FR('FR-2.1', 'Submit a report by selecting a location and a depth on the body or vehicle scale; derive severity from depth automatically.'),
  FR('FR-2.2', 'Write the report to device storage and display it on the author’s own map before attempting any transmission.'),
  FR('FR-2.3', 'Attach an optional photo captured in the application at the time of reporting; the device photo library is not offered as a source.'),
  FR('FR-2.4', 'Carry the existence and content hash of a photo in the report event itself, queueing the image separately at the lowest priority, so every device computes the same confidence whether or not the image has arrived.'),
  FR('FR-2.5', 'Present a report without a photo as less corroborated rather than as doubtful, and never require a photo to submit.'),
  FR('FR-2.6', 'Record two independent presence signals with each event: asserted position, and relay attestation — how many nearby devices received it directly over short-range radio.'),
  FR('FR-2.7', 'Count corroboration only from distinct devices, weighting a confirmation by relay attestation rather than by asserted position.'),
  FR('FR-2.8', 'Embed the author’s display name in the event at creation, so it renders on a receiving device with no lookup. It is excluded from the confidence calculation.'),
  AC('AC-2', 'A submitted report appears on the author’s map before transmission, and reaches a second device over the relay with both devices offline.'),

  H2('7.3 Real-Time Notifications'),
  STORY('As a resident, I want to be told about flooding near my home or on my route without opening the application.'),
  FR('FR-3.1', 'Evaluate geofences locally on every event insert, so alerts fire with no push server and no connectivity.'),
  FR('FR-3.2', 'Support a home radius and saved routes as alert scopes.'),
  FR('FR-3.3', 'Relay PAGASA and NDRRMC advisories verbatim, presented alongside but visually distinct from community reports.'),
  FR('FR-3.4', 'Escalate a rescue request in range to a critical alert that overrides silent mode.'),
  AC('AC-3', 'An alert fires on a device in airplane mode when a matching event arrives over the relay.'),

  H2('7.4 SOS / Rescue Request'),
  STORY('As someone trapped, I want to call for help and know it was received, with no signal.'),
  FR('FR-4.1', 'Raise a rescue request from a long-press, carrying location, party size, composition and water level.'),
  FR('FR-4.2', 'Structure the payload in three cryptographically separated parts: a cleartext routing header, a rescue body readable by responders and officials, and a medical envelope readable by officials alone.'),
  FR('FR-4.3', 'Broadcast over every available transport, and return the responder’s acknowledgement down the same chain.'),
  FR('FR-4.4', 'Never transmit photos by SMS or relay.'),
  FR('FR-4.5', 'When no peer is reachable, enter the rescue-card state: beacon, alarm tone, screen flash, and a scannable card.'),
  FR('FR-4.6', 'Allow an acknowledging responder or any barangay official to mark a request unfounded on arrival, as a signed attributable event; a responder mark is advisory, an official mark authoritative.'),
  FR('FR-4.7', 'Lower the routing priority of later requests from a device with unfounded marks against it, without ever suppressing them, restoring priority on successful appeal to the barangay.'),
  AC('AC-4', 'A request raised on an offline device reaches a second offline device, and the acknowledgement returns to the sender.'),

  H2('7.5 Offline-First Design'),
  FR('FR-5.1', 'Open and operate every core path from pre-downloaded map tiles and the local event store.'),
  FR('FR-5.2', 'Queue all outbound events with a priority class, and offer them to every transport as it becomes available.'),
  FR('FR-5.3', 'Deduplicate by content hash on arrival, so re-delivery over multiple transports is harmless.'),
  FR('FR-5.4', 'Carry forward: relay and later upload events the device did not author.'),
  FR('FR-5.5', 'Display transport state honestly — what has been sent, what is queued, and what is stored only on this phone.'),
  AC('AC-5', 'Every acceptance criterion above is demonstrated with the device in airplane mode.'),

  H2('7.6 to 7.9  Additional features'),
  CALLOUT('Built only when all of 7.1 to 7.5 pass on real hardware in airplane mode.'),
  table(['Feature', 'Key requirements'], [
    ['7.6 Official Verification', 'Issue official status from a phone in the field with no connectivity; sign it against the LGU keys in the regional pack so it verifies offline; allow any official to reverse another’s, retaining both; require a second official before a clearance lowers a conflicting location’s severity'],
    ['7.7 Evacuation Centre Directory', 'Bundled offline directory with capacity and activation state; capacity updates propagate by every transport; render distance and route warnings from local data'],
    ['7.8 Family and Household Check-In', 'A household circle joined by local exchange; “I am safe” as a single-tap event that travels the relay; no server, and no message body to congest the network'],
    ['7.9 Offline Route Check', 'Evaluate a route against local flood state and mark impassable segments; suggest an alternative from the offline graph; state plainly when it cannot find one'],
  ], [2500, 7580]),
  SPACER,

  H2('7.10 LGU Dashboard'),
  P('A responsive web console, built last. Authenticated accounts scoped to one LGU with an audit log; live area-wide map using the same severity, confidence and staleness encoding; official verification at scale; rescue queue with grouped incidents; evacuation capacity management; post-event export; and an explicit degraded state when the server is unreachable. Nothing in the mobile application depends on it.'),
);

// ============ 8. ACCESSIBILITY ============
children.push(
  H1('8. Accessibility and Inclusion'),
  BB('Language. ', 'Filipino first, English secondary, on every core screen. Regional languages are a post-launch commitment.'),
  BB('Literacy. ', 'Every core action is reachable by icon and body-scale illustration without reading prose. Depth is chosen from pictures of a person and a vehicle.'),
  BB('Vision. ', 'Minimum 4.5:1 contrast on all text and 3:1 on severity fills, in all three modes. Layout must hold at maximum system font scale.'),
  BB('Motor. ', 'One-handed reach for map, report and SOS. Minimum 48 dp targets, raised in Storm mode. No gesture is the only route to any action.'),
  BB('Device inclusion. ', 'Android 8 and above on budget handsets. Feature phones that cannot run the application are reached through the SMS path.'),
  BB('Colour independence. ', 'Severity is carried by shape and label as well as colour; the conflicting state is a hatch, not a hue.'),
);

// ============ 9. PRIVACY ============
children.push(
  H1('9. Privacy and Consent'),
  BB('Minimum collection. ', 'Residents provide a name and home barangay once, at first run. No email, no password, no address, no contacts. Volunteers additionally provide a mobile number and what they can bring.'),
  BB('Registration is identification, not authentication. ', 'Nothing entered is verified against anything, because there is nothing offline to verify against. It exists for attribution — putting an accountable name on a report so that filing a false one has a social cost. It is never used for access control, permissions, or confidence weighting. If a name could raise confidence, typing one would be a free way to raise it.'),
  BB('Name visibility. ', 'A report or rescue request displays its author’s name to any user who opens it. The displayed form is a first name and last initial with the barangay, never a full legal name or a doorstep.'),
  BB('Tiered disclosure of rescue requests ', 'is enforced cryptographically, not by interface convention: routing header in cleartext, rescue body to responder and official keys, medical envelope to official keys alone. A volunteer never receives medical context, because a role obtainable by registration should not carry the most sensitive field in the system.'),
  BB('Consent is revocable within the limits of a replicated store. ', 'A withdrawal is itself an event and removes the item wherever it reaches. Copies already carried to other devices cannot be recalled, because there is no authority that can reach into a stranger’s phone — and that same property is what makes the product work without a network. This limit is disclosed at the point of collection rather than in a policy, so no one is told their data was erased when it was withdrawn.'),
  BB('Regulatory position. ', 'The LGU is the data controller under RA 10173. A privacy impact assessment and legal review are prerequisites before any real deployment, and are scheduled in Phase 3.'),
);

// ============ 10. DATA SOURCES ============
children.push(
  H1('10. Data Sources'),
  table(['Source', 'Provides', 'Trust treatment'], [
    ['Resident reports', 'Street-level flood conditions', 'Weighted by relay attestation and distinct-device corroboration; expires with staleness'],
    ['Official verification', 'Authoritative local status', 'Signature-checked offline against LGU keys; overrides crowd display, with the crowd layer retained'],
    ['PAGASA / NDRRMC', 'National advisories and warnings', 'Relayed verbatim; never blended with or derived into community data'],
    ['OpenStreetMap', 'Base map and offline vector tiles', 'Pre-downloaded per region'],
    ['Barangay boundary set', 'Offline resolution of GPS to barangay', 'Bundled with the regional pack'],
    ['LGU evacuation directory', 'Centres, capacity, activation', 'Provided by the LGU; updated by officials in the field'],
  ], [2200, 2900, 4980]),
);

// ============ 11. ARCHITECTURE ============
children.push(
  H1('11. Architecture'),
  RP([['Event-sourced and local-first. ', { b: true }], ['An immutable append-only event store feeds a deterministic reducer, which produces the displayed state. Every device holds its own store and computes its own map.']]),
  RP([['The local database is a replica, not a cache. ', { b: true }], ['It is not a holding pen flushed into the server and emptied. Two devices holding the same events display the same status, which is what makes an unsynchronised device trustworthy rather than merely stale.']]),
  RP([['Three transports', { b: true }], [', offered in order by a transport manager:']]),
  BB('Server sync — ', 'batch POST, idempotent on event ID; pull by bounding box and cursor. Carries photos.'),
  BB('Device-to-device relay — ', 'Bluetooth and Wi-Fi Direct. Devices exchange event-ID lists and transfer the difference. No server involved.'),
  BB('SMS — ', 'a bit-packed encoding for the cellular-but-no-data case, which also reaches feature phones.'),
  SPACER,
  RP([['Stack. ', { b: true }], ['Kotlin and Jetpack Compose, min SDK 26; MapLibre with pre-downloaded offline tiles; Room over SQLite; Nearby Connections for the relay; SmsManager for the SMS path. Server: Node and Express with the built-in node:sqlite module — Express is the only dependency. Dashboard: one HTML page with MapLibre GL JS. FCM is an optional sync-wake optimisation; every alert fires without it.']]),
  RP([['What is lost with no server: ', { b: true }], ['reach beyond relay range, the dashboard, official feed ingestion, SMS gateway bridging. ']]),
  RP([['What survives: ', { b: true }], ['the map, reporting, confirm and dispute, local notifications, and SOS to nearby phones.']]),
);

// ============ 12. DATA FLOW ============
children.push(
  H1('12. Data Flow'),
  RP([['Creating an event. ', { b: true }], ['The user acts. The event is signed and written to the local store. The reducer recomputes, so the author sees the result immediately. The event enters the outbound queue with a priority class, and the transport manager offers it to every available transport.']]),
  RP([['Receiving an event. ', { b: true }], ['Devices discard duplicates by content hash, store what is new, recompute their own state, and evaluate their own geofences — which is why notifications fire with no push server.']]),
  RP([['The fully offline path. ', { b: true }], ['A report commits locally and appears on the author’s map. No data connection and no SMS exist, so the transport manager hands it to the relay. A neighbour’s phone receives it, renders it, and fires that neighbour’s local alert. The warning has done useful work with no infrastructure whatsoever.']]),
  RP([['Carry-forward is the bridge. ', { b: true }], ['A device that picked up events over the relay uploads all of them on reconnect, including ones it never authored. That is how a report from a phone that never had signal reaches the server — someone else carries it out.']]),
);

// ============ 13. NFRs ============
children.push(
  H1('13. Non-Functional Requirements'),
  table(['ID', 'Requirement'], [
    ['NFR-1', 'Every core path functions with no network connection; offline is the default assumption, not a degraded mode'],
    ['NFR-2', 'Cold start to an interactive map in under 3 seconds on a min-spec device'],
    ['NFR-3', 'Battery cost of relay and background evaluation is bounded and disclosed; Survival mode suspends non-essential work'],
    ['NFR-4', 'The reducer is deterministic: two devices with the same events display the same status'],
    ['NFR-5', 'The event store is bounded on device — events past retention are compacted into the state they produce, and compaction is itself deterministic'],
    ['NFR-6', 'An SMS-encoded report fits one 160-character message'],
    ['NFR-7', 'Official actions verify offline against keys distributed in the regional pack, never against a server session'],
    ['NFR-8', 'Layout holds at maximum system font scale, in all three modes'],
    ['NFR-9', 'Transport state is always visible and honest; the application never implies delivery it cannot confirm'],
  ], [1100, 8980]),
);

// ============ 14. TESTING ============
children.push(
  H1('14. Testing Approach'),
  BB('Airplane-mode test, daily. ', 'The claim the product is named for is verified every build day, not at the end. A build that has not been opened in airplane mode is unverified.'),
  BB('Two-device determinism test. ', 'Feed the same event set to two devices in different orders; the displayed state must match. This is the check that protects NFR-4 and it is the one worth automating.'),
  BB('Three-device relay test. ', 'A and C out of range, B between them. Verifies multi-hop carry rather than a single pairing.'),
  BB('Seeded fixtures from day one. ', 'An empty map demos terribly and debugs worse.'),
  BB('Tier enforcement test. ', 'A responder credential must fail to decrypt a medical envelope. This is a privacy claim, so it is tested rather than asserted.'),
  BB('Manual accessibility pass ', 'at maximum font scale in all three modes.'),
  BB('Named gap: ', 'no field measurement of relay range, delivery rate or battery cost in a real barangay, and no OEM device matrix. Both are prerequisites before real deployment and are stated as such rather than implied to be done.'),
);

// ============ 15. SUCCESS METRICS ============
children.push(
  H1('15. Success Metrics'),
  table(['Metric', 'Target'], [
    ['Time to file a report', 'Under 20 seconds, no typing'],
    ['Core paths usable offline', '100% — any failure is a defect, not a degradation'],
    ['Relay delivery, two devices in range', 'Report visible on the second device within 30 seconds'],
    ['Acknowledgement round trip, both offline', 'Under 60 seconds across one hop'],
    ['Determinism', 'Zero divergence between devices holding identical event sets'],
    ['Conflicting state', 'Rendered as conflicting in 100% of contradiction cases; never averaged'],
    ['Medical tier', 'Zero decryptions by a responder-tier credential'],
  ], [3400, 6680]),
);

// ============ 16. ASSUMPTIONS ============
children.push(
  H1('16. Assumptions and Dependencies'),
  H2('Assumptions'),
  B('Enough neighbours run the application for the relay to find a peer. This is the single largest risk to the premise, and it is unmeasured.'),
  B('Residents will report honestly in the ordinary case, and attribution plus weighted corroboration is sufficient against the rest.'),
  B('A barangay can register volunteers before or between events, rather than during one.'),
  B('Phones retain charge long enough to matter; Survival mode extends this but does not solve it.'),
  H2('Dependencies'),
  B('Google Play Services for Nearby Connections and fused location.'),
  B('Runtime grants for location, SMS and nearby devices — available because the build is sideloaded rather than Play-distributed.'),
  B('LGU cooperation for key issuance, the evacuation directory, and dashboard operation.'),
  B('PAGASA and NDRRMC feeds remaining publicly retrievable.'),
  B('Device storage for regional tile packs.'),
  SPACER,
  CALLOUT('Two limits stated rather than designed around. A coordinated group physically present in a barangay can raise confidence on a false report, because presence is the strongest signal available and they have it. And a report authored offline reaches neighbouring phones before any server sees it, so no server-side filter can act before local spread. Both are consequences of an offline-first design, and both are accepted.'),
);

// ============ 17. PHASES ============
children.push(
  H1('17. Implementation Phases'),
  table(['Phase', 'Contents', 'Exit condition'], [
    ['1 — Offline core', 'Offline map and tiles; reporting with derived severity; confirm and dispute; the reducer with conflict and staleness rules; local notifications', 'Every path demonstrated in airplane mode on real hardware'],
    ['2 — Relay and SOS', 'Nearby Connections relay with carry-forward; SOS with three-part payload; responder acknowledgement; rescue-card state; unfounded marking; credentials with a validity window', 'An SOS raised offline reaches a second offline device and the acknowledgement returns'],
    ['3 — Trust and inclusion', 'Official verification; volunteer registration and barangay activation; evacuation directory; SMS path; legal review and RA 10173 privacy impact assessment', 'An official status issued offline propagates by relay and verifies on a device that has never been online'],
    ['4 — Reach', 'Family check-in; offline route check; fixed relay nodes at barangay halls; field measurement of relay range and battery cost', 'Measured relay performance supports or refutes the core premise, and the product is adjusted accordingly'],
    ['5 — LGU dashboard', 'Authenticated LGU accounts with audit log; area-wide map; verification at scale; rescue queue; capacity management; export; degraded state', 'An officer verifies a report and it reaches a resident device — with the five core features fully operable with the dashboard switched off'],
  ], [1600, 4600, 3880]),
  SPACER,
  P('Companion documents: PROJECT_CONCEPT.md (full architecture), BUILD_PLAN.md (schedule and risk register), ROUTING.md (prototype routing matrix), and the 29-artboard hi-fi design canvas.', { i: true }),
);

// ---------- document ----------
const doc = new Document({
  creator: 'KaAlerto',
  title: 'KaAlerto — Product Requirements Document',
  description: 'Concise product requirements for an offline-first community flood mapping and rescue application',
  features: { updateFields: true },
  styles: {
    default: {
      document: { run: { font: 'Calibri', size: 20, color: '1A1A1A' }, paragraph: { spacing: { line: 264 } } },
      heading1: { run: { font: 'Calibri', size: 30, bold: true, color: NAVY }, paragraph: { spacing: { before: 280, after: 120 } } },
      heading2: { run: { font: 'Calibri', size: 23, bold: true, color: NAVY }, paragraph: { spacing: { before: 200, after: 90 } } },
      heading3: { run: { font: 'Calibri', size: 21, bold: true, color: GREY }, paragraph: { spacing: { before: 160, after: 70 } } },
    },
  },
  sections: [{
    properties: {
      page: { size: { width: PAGE_W, height: PAGE_H }, margin: { top: MARGIN, bottom: MARGIN, left: MARGIN, right: MARGIN } },
    },
    headers: {
      default: new Header({
        children: [new Paragraph({
          children: [new TextRun({ text: 'KaAlerto — Product Requirements Document', size: 16, color: GREY })],
          alignment: AlignmentType.RIGHT,
          border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: 'D9D9D9', space: 5 } },
        })],
      }),
    },
    footers: {
      default: new Footer({
        children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [new TextRun({ children: ['Page ', PageNumber.CURRENT, ' of ', PageNumber.TOTAL_PAGES], size: 16, color: GREY })],
        })],
      }),
    },
    children,
  }],
});

Packer.toBuffer(doc).then((buf) => {
  fs.writeFileSync(process.argv[2], buf);
  console.log('written:', process.argv[2], (buf.length / 1024).toFixed(1) + ' KB');
});
