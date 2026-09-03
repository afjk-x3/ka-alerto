const fs = require('fs');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType,
  BorderStyle, PageBreak, Header, Footer, PageNumber,
} = require('docx');

const PAGE_W = 12240, PAGE_H = 15840, MARGIN = 1440;
const NAVY = '1F3864', ACCENT = 'C0392B', GREY = '595959';

const H1 = (t) => new Paragraph({ text: t, heading: HeadingLevel.HEADING_1, spacing: { before: 400, after: 180 } });
const H2 = (t) => new Paragraph({ text: t, heading: HeadingLevel.HEADING_2, spacing: { before: 300, after: 130 } });

const P = (t, opts = {}) => new Paragraph({
  children: [new TextRun({ text: t, italics: !!opts.italics, bold: !!opts.bold, color: opts.color })],
  spacing: { after: opts.after === undefined ? 150 : opts.after },
});

// rich paragraph — parts are [text, {b, i}]
const RP = (parts, opts = {}) => new Paragraph({
  children: parts.map(([text, o = {}]) => new TextRun({ text, bold: !!o.b, italics: !!o.i, color: o.color })),
  spacing: { after: opts.after === undefined ? 150 : opts.after },
});

// rich bullet
const RB = (parts, level = 0) => new Paragraph({
  children: parts.map(([text, o = {}]) => new TextRun({ text, bold: !!o.b, italics: !!o.i })),
  bullet: { level },
  spacing: { after: 90 },
});

const B = (t, level = 0) => new Paragraph({ text: t, bullet: { level }, spacing: { after: 90 } });

const QUOTE = (t) => new Paragraph({
  children: [new TextRun({ text: t, color: '333333' })],
  spacing: { before: 120, after: 200 },
  indent: { left: 200 },
  border: { left: { style: BorderStyle.SINGLE, size: 18, color: ACCENT, space: 14 } },
});

const CALLOUT = (parts) => new Paragraph({
  children: parts.map(([text, o = {}]) => new TextRun({ text, bold: o.b !== false, italics: !!o.i, color: NAVY })),
  spacing: { before: 160, after: 200 },
  indent: { left: 200 },
  border: { left: { style: BorderStyle.SINGLE, size: 18, color: NAVY, space: 14 } },
});

const children = [];

// ---------- title page ----------
children.push(
  new Paragraph({ text: '', spacing: { after: 2200 } }),
  new Paragraph({
    children: [new TextRun({ text: 'KAALERTO', bold: true, size: 60, color: NAVY })],
    alignment: AlignmentType.CENTER, spacing: { after: 120 },
  }),
  new Paragraph({
    children: [new TextRun({ text: 'Ideation', size: 32, color: GREY })],
    alignment: AlignmentType.CENTER, spacing: { after: 420 },
  }),
  new Paragraph({
    children: [new TextRun({
      text: 'A community flood-intelligence and rescue-coordination app for Philippine barangays, designed to keep working when internet and cellular infrastructure fail.',
      size: 24, italics: true, color: GREY,
    })],
    alignment: AlignmentType.CENTER, spacing: { after: 600 },
    indent: { left: 900, right: 900 },
  }),
  new Paragraph({ children: [new PageBreak()] }),
);

// ---------- Problem ----------
children.push(
  H1('Problem'),
  QUOTE('The Philippines is highly exposed to frequent natural hazards, experiencing an average of twenty tropical cyclones annually. When severe typhoons strike, infrastructure collapse often severs internet and cellular connections, rendering centralized, cloud-based early warning systems inaccessible to vulnerable communities precisely when they are most needed. Additionally, local communities struggle to translate broad national weather data into actionable, street-level evacuation protocols, and there is a lack of affordable, localized water-level sensors to provide real-time alerts.'),
);

// ---------- Resolution ----------
children.push(
  H1('How the System Resolves the Problem'),
  P('The problem statement contains three distinct failures. Each has a distinct answer.'),

  H2('1. Connectivity failure → the system does not depend on connectivity'),
  RP([
    ['A warning system that requires the internet is a warning system that switches itself off during the emergency. KaAlerto is therefore built as a '],
    ['local-first application', { b: true }],
    [' rather than a client to a cloud service: the phone holds its own copy of the data and computes its own answers, and the server is an optional enhancement rather than a prerequisite.'],
  ]),
  RB([
    ['The map opens and works with no connection', { b: true }],
    [', from downloaded offline tile packs. GPS positioning needs no network, so the user’s location is available even in a total blackout.'],
  ]),
  RB([
    ['Every report and SOS is stored on the device the moment it is created', { b: true }],
    [' — it is immediately useful to the person who wrote it, and is transmitted later, automatically, whenever a path becomes available.'],
  ]),
  RB([
    ['When the network is gone entirely, reports and rescue requests travel phone-to-phone', { b: true }],
    [' over Bluetooth and Wi-Fi Direct, hopping between nearby devices until one of them reaches connectivity and uploads on everyone’s behalf. The mesh only has to reach '],
    ['one', { i: true }],
    [' connected device, not the server — one person driving to higher ground can carry an entire neighbourhood’s data out with them.'],
  ]),
  RB([
    ['Where the data network is down but the cellular network survives', { b: true }],
    [', compact SMS carries reports and rescue requests, which also reaches feature phones that cannot run the app at all.'],
  ]),
  RB([
    ['Notifications are triggered locally', { b: true }],
    [', by the device evaluating its own data against the user’s watched locations, so alerts continue to fire with no push server and no signal.'],
  ]),
  CALLOUT([
    ['The result: the system’s capability degrades in fidelity rather than switching off. '],
    ['Full data connection, then SMS, then phone-to-phone relay, then a device that still holds a usable map and can still call for help.', { b: false }],
  ]),

  H2('2. Resolution failure → the community produces the street-level layer'),
  P('National forecasts are accurate but coarse. They can tell a resident that a storm signal has been raised over their province; they cannot tell them whether the road to their child’s school is passable. That last mile of information exists only in the heads of the people standing in the water — so the app’s job is to collect it, structure it, and give it back.'),
  RB([
    ['Reports are attached to specific roads and areas', { b: true }],
    [', turning a provincial advisory into a street-level picture.'],
  ]),
  RB([
    ['Severity is expressed as a decision, not a measurement.', { b: true }],
    [' “Impassable for cars” and “waist-deep” are things an ordinary person can both report accurately and act on immediately, without interpreting rainfall figures.'],
  ]),
  RB([
    ['Confirm and dispute, plus a visible confidence indicator and automatic expiry', { b: true }],
    [', mean the map communicates not just what is being reported but '],
    ['how much to trust it and how fresh it is', { i: true }],
    [' — the difference between usable local knowledge and rumour.'],
  ]),
  RB([
    ['Radius and saved-route notifications', { b: true }],
    [' convert the shared map into a personal, actionable warning: not “a storm is coming” but “the road you take home is now impassable.”'],
  ]),
  RB([
    ['Official PAGASA and NDRRMC updates sit alongside the community layer', { b: true }],
    [', so authoritative national guidance and local ground truth are read together rather than in separate places.'],
  ]),

  H2('3. Sensor scarcity → the community is the sensor network'),
  RP([
    ['Dedicated water-level sensors are expensive, and a budget large enough to instrument every chokepoint in a municipality does not exist at barangay level. The app sidesteps the constraint rather than trying to fund its way around it: '],
    ['every resident with a phone becomes a distributed water-level sensor, at no hardware cost.', { b: true }],
  ]),
  RB([
    ['The body and vehicle water-level scales are the measurement instrument.', { b: true }],
    [' They are deliberately coarse because coarse readings are what untrained people can produce reliably and consistently — and “a car cannot pass” is more directly actionable than a depth in centimetres anyway.'],
  ]),
  RB([
    ['Coverage beats precision here.', { b: true }],
    [' A funded sensor programme might instrument a handful of fixed points; residents are already everywhere, including the streets no budget would ever cover.'],
  ]),
  RB([
    ['Confirm/dispute and staleness expiry act as the calibration layer', { b: true }],
    [', filtering error and out-of-date readings the way a sensor network relies on maintenance and validation.'],
  ]),
  RP([
    ['The honest trade-off: human reports are '],
    ['episodic', { i: true }],
    [' rather than continuous, and depend on someone being present and willing to report. Physical sensors would complement this well in future. But for the coverage a community can achieve today, at a cost a barangay can actually afford, people are the only sensor network available — and this app is what turns them into one.'],
  ]),
);

// ---------- Features ----------
children.push(
  new Paragraph({ children: [new PageBreak()] }),
  H1('Features'),

  H2('1. Interactive Flood Map'),
  RP([
    ['The primary surface of the app, and the first thing a user sees. It answers the question a resident actually faces during a storm: '],
    ['is this specific street passable right now?', { i: true }],
  ]),
  RB([['Base map', { b: true }], [' (OpenStreetMap or Google Maps SDK) showing the user’s live location.']]),
  RB([['Crowdsourced flood markers and overlays', { b: true }], [' applied to roads and areas, so a flooded road reads as a flooded road rather than a pin floating over one.']]),
  RB([['Severity levels', { b: true }], [', three tiers:']]),
  B('Passable with caution', 1),
  B('Impassable for cars', 1),
  B('Impassable for all', 1),
  RB([['Timestamp of the last report', { b: true }], [' on every marker, so users can judge how current the information is.']]),
  RB([['Confidence indicator', { b: true }], [' derived from the number of confirming reports — distinguishing a single unverified sighting from a road four neighbours have independently confirmed.']]),
  RB([['Filtering', { b: true }], [' by severity, by recency, or by barangay/district.']]),

  H2('2. Crowdsourced Flood Reporting'),
  P('The map is only as good as the reports feeding it, so reporting has to be fast enough to do in the rain, one-handed, in the dark.'),
  RB([['Report a flooded road or area', { b: true }], [' by tapping a location on the map or using the current GPS position.']]),
  RB([['Water-level estimate', { b: true }], [', in whichever frame of reference the reporter finds natural:']]),
  B('Body-referenced — ankle / knee / waist / chest', 1),
  B('Vehicle-referenced — truck / car / motorcycle / not passable', 1),
  RB([['Optional photo', { b: true }], [' attached to the report.']]),
  RB([['Automatic timestamp', { b: true }], [' on submission.']]),
  RB([['Confirm or dispute existing reports', { b: true }], [' — Waze-style trust verification, where the community itself validates what it sees. Reports gain credibility through corroboration rather than asserting it.']]),
  RB([
    ['Automatic expiry', { b: true }],
    [', with reports flagged as '],
    ['stale', { i: true }],
    [' after a set time unless someone re-confirms them. Floodwater changes fast, and information that was true an hour ago can be dangerous now.'],
  ]),

  H2('3. Real-Time Notifications'),
  P('Users should not have to open the app to learn that something changed near them.'),
  RB([['New flood report within a user-defined radius', { b: true }], [' of their home or current location, or along a saved route.']]),
  RB([['A previously flooded area near them marked as cleared', { b: true }], [', so people are not stranded by information that is out of date in the other direction.']]),
  RB([['Official PAGASA/NDRRMC updates', { b: true }], [' delivered when connectivity allows.']]),
  RB([['Graceful offline degradation', { b: true }], [' — notifications must continue to function when the network does not, which is handled by the offline-first design below rather than treated as an exception.']]),

  H2('4. SOS / Rescue Request'),
  P('The feature that matters most on the worst night, and the one that most needs to work without infrastructure.'),
  RB([['One-tap “I need rescue”', { b: true }], [' carrying live GPS position, with optional context: number of people, medical needs, current water level.']]),
  RB([['Routing to help', { b: true }], [' — to nearby rescuers, to barangay responders, and to a shared rescue-request board visible to volunteers and the LGU.']]),
  RB([
    ['Status updates', { b: true }],
    [' visible to the person who sent it: '],
    ['received → en route → rescued', { i: true }],
    ['. Someone waiting on a roof deserves to know whether anyone has seen their request.'],
  ]),
  RB([['Peer-to-peer broadcast to nearby app users', { b: true }], [' when there is no server connectivity at all, so a rescue request can still travel even with every tower down.']]),

  H2('5. Offline-First Design'),
  P('This is the core design constraint of the project, not an add-on. Every feature above must remain useful when the network fails, because that is precisely when they are needed.'),
  RB([['Downloadable offline map tile packs', { b: true }], [' per region, so the map opens and works with no connection.']]),
  RB([['Local storage with queue-and-sync', { b: true }], [' — flood reports and SOS requests are stored on the device immediately and transmitted automatically once connectivity returns.']]),
  RB([['Graceful low-bandwidth and intermittent handling', { b: true }], [' — compression and retry with backoff, so a weak or flapping connection is used efficiently rather than wasted.']]),
  RB([['Alternative transport when cellular and internet are fully down:', { b: true }]]),
  RB([['Bluetooth / Wi-Fi Direct mesh relay', { b: true }], [' — reports and SOS requests hop device-to-device between nearby phones until one of them reaches connectivity and uploads on everyone’s behalf.']], 1),
  RB([['SMS-based fallback', { b: true }], [' for reporting and alerting, covering feature phones and data-down scenarios.']], 1),
  RB([['Minimised battery and data usage', { b: true }], [', since power outages routinely outlast the storm itself and a phone that dies is a user the system can no longer reach or help.']]),
);

const doc = new Document({
  creator: 'KaAlerto',
  title: 'KaAlerto — Ideation',
  description: 'Problem framing and feature concept for an offline-first community flood mapping and rescue application',
  styles: {
    default: {
      document: { run: { font: 'Calibri', size: 22, color: '1A1A1A' }, paragraph: { spacing: { line: 276 } } },
      heading1: { run: { font: 'Calibri', size: 34, bold: true, color: NAVY }, paragraph: { spacing: { before: 400, after: 180 } } },
      heading2: { run: { font: 'Calibri', size: 26, bold: true, color: NAVY }, paragraph: { spacing: { before: 300, after: 130 } } },
    },
  },
  sections: [{
    properties: {
      page: { size: { width: PAGE_W, height: PAGE_H }, margin: { top: MARGIN, bottom: MARGIN, left: MARGIN, right: MARGIN } },
    },
    headers: {
      default: new Header({
        children: [new Paragraph({
          children: [new TextRun({ text: 'KaAlerto — Ideation', size: 18, color: GREY })],
          alignment: AlignmentType.RIGHT,
          border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: 'D9D9D9', space: 6 } },
        })],
      }),
    },
    footers: {
      default: new Footer({
        children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [new TextRun({ children: ['Page ', PageNumber.CURRENT, ' of ', PageNumber.TOTAL_PAGES], size: 18, color: GREY })],
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
