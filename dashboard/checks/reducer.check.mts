// Runs the phone's reducer test cases (ReducerTest, OfficialOverrideTest, WithdrawTest in
// android/app/src/test/.../data/) against the dashboard's port, so a change to one side that is
// not mirrored fails here. Run: npm run check
import assert from 'node:assert/strict';
import { summarize, summarizeAll, severityOrdinal } from '../src/lib/reducer.ts';

const now = 1_700_000_000_000;
const ref = 'wz0j1abc';
const near = { lat: 18.1709, lon: 120.6058 };
const farLat = 18.179; // ~900 m north: outside the 500 m proximate band

const ttl = (s: string | null) => (s === 'S1' ? 120 : s === 'S2' ? 240 : s === 'S3' ? 360 : 60);

type Opts = { role?: string; minutesAgo?: number; lat?: number; lon?: number; type?: string; id?: string; expiresAt?: number; ref?: string | null };

function ev(author: string, severity: string | null, o: Opts = {}) {
  const minutesAgo = o.minutesAgo ?? 5;
  const ts = now - minutesAgo * 60_000;
  const type = o.type ?? 'flood_report';
  return {
    id: o.id ?? `${author}-${severity}-${minutesAgo}-${type}`,
    type,
    lat: o.lat ?? near.lat,
    lon: o.lon ?? near.lon,
    featureRef: o.ref === undefined ? ref : o.ref,
    severity,
    waterLevel: null,
    authorId: author,
    authorName: author,
    authorRole: o.role ?? 'resident',
    timestampMs: ts,
    expiresAt: o.expiresAt ?? ts + ttl(severity) * 60_000,
    origin: 'local',
    hopCount: 0,
    note: null,
    disputeReason: null,
    payload: null,
  };
}

const sum = (...events: ReturnType<typeof ev>[]) => summarize(ref, events, now)!;
const between = (x: number, lo: number, hi: number) => x >= lo && x <= hi;

let failed = 0;
let passed = 0;
function test(name: string, fn: () => void) {
  try {
    fn();
    passed++;
  } catch (e) {
    failed++;
    console.error(`FAIL  ${name}\n      ${(e as Error).message.split('\n')[0]}`);
  }
}

// ---- ReducerTest ----
test('a feature with no events has no summary', () => {
  assert.equal(summarize(ref, [], now), null);
  assert.equal(summarize('somewhere-else', [ev('a', 'S2')], now), null);
});

test('summarizeAll groups by featureRef and ignores events that have none', () => {
  const other = { ...ev('b', 'S1'), id: 'other', featureRef: 'other-ref' };
  const unplaced = { ...ev('c', 'S3'), id: 'unplaced', featureRef: null };
  const all = summarizeAll([ev('a', 'S2'), other, unplaced], now);
  assert.deepEqual(new Set(all.map((s) => s.featureRef)), new Set([ref, 'other-ref']));
});

test("Rule A's ceiling holds until Rule B's bar is met", () => {
  assert.equal(sum(ev('a', 'S3'), ev('b', 'S2')).severity, 'S3');
  assert.equal(sum(ev('a', 'S3'), ev('b', 'S2'), ev('c', 'S2')).severity, 'S2');
});

test('a report too decayed to matter cannot set the ceiling', () => {
  assert.equal(sum(ev('old', 'S1', { minutesAgo: 40 * 60 }), ev('now', 'S0')).severity, 'S0');
});

test('with every report decayed away the feature reads S0 and unverified', () => {
  const s = sum(ev('a', 'S3', { minutesAgo: 100 * 60 }));
  assert.equal(s.severity, 'S0');
  assert.equal(s.bucket, 'unverified');
  assert.equal(s.confidence, 0);
});

test('one nearby resident cannot pull the display down from S3', () => {
  const s = sum(ev('a', 'S3'), ev('b', 'S0'));
  assert.ok(s.isConflicted);
  assert.equal(s.severity, 'SX');
});

test('two proximate residents do pull it down, and that pre-empts SX', () => {
  const s = sum(ev('a', 'S3'), ev('b', 'S0'), ev('c', 'S0'));
  assert.equal(s.severity, 'S0');
  assert.ok(!s.isConflicted);
});

test('two distant voices do not: Rule B counts proximate observers only', () => {
  const s = sum(
    ev('a', 'S3'),
    ev('b', 'S0', { lat: farLat, type: 'dispute', id: 'far-1' }),
    ev('c', 'S0', { lat: farLat, type: 'dispute', id: 'far-2' }),
  );
  assert.equal(s.severity, 'SX');
  assert.ok(s.isConflicted);
});

test('SX is never a severity in the ladder', () => {
  const s = sum(ev('a', 'S3'), ev('b', 'S0'));
  assert.equal(s.severity, 'SX');
  assert.equal(severityOrdinal('SX'), 0);
  assert.equal(s.confidence, 0);
  assert.equal(s.bucket, 'unverified');
});

test('agreement at two adjacent danger tiers is not a conflict', () => {
  const s = sum(ev('a', 'S3'), ev('b', 'S2'));
  assert.ok(!s.isConflicted);
  assert.equal(s.severity, 'S3');
});

test("one author's repeated reports do not stack into consensus", () => {
  const s = sum(ev('a', 'S2', { minutesAgo: 30 }), ev('a', 'S2', { minutesAgo: 20 }), ev('a', 'S2', { minutesAgo: 10 }));
  assert.equal(s.severity, 'S2');
  assert.equal(s.bucket, 'unverified');
});

test("a confirm's own coordinates do not drag the feature anchor", () => {
  const s = sum(ev('a', 'S2'), ev('b', 'S2', { lat: farLat, type: 'confirm' }));
  assert.ok(Math.abs(s.lat - near.lat) < 1e-6);
  assert.ok(Math.abs(s.lon - near.lon) < 1e-6);
});

test('a responder outweighs a resident when they disagree at the same distance', () => {
  const split = sum(ev('a', 'S3'), ev('b', 'S2'));
  const backed = sum(ev('a', 'S3'), ev('b', 'S2'), ev('c', 'S3', { role: 'responder' }));
  assert.ok(backed.confidence > split.confidence, `${split.confidence} -> ${backed.confidence}`);
});

test('a single report is never more than unverified', () => {
  assert.equal(sum(ev('a', 'S2')).bucket, 'unverified');
});

test('agreeing proximate reports reach confirmed', () => {
  const s = sum(ev('a', 'S2'), ev('b', 'S2'), ev('c', 'S2'), ev('d', 'S2'));
  assert.equal(s.severity, 'S2');
  assert.equal(s.bucket, 'confirmed');
  assert.ok(s.confidence >= 0.65);
});

test('confidence never leaves the unit interval', () => {
  for (const s of [
    sum(ev('a', 'S2')),
    sum(ev('a', 'S3'), ev('b', 'S0')),
    sum(ev('a', 'S1'), ev('b', 'S1'), ev('c', 'S1')),
    sum(ev('a', 'S3', { minutesAgo: 5000 })),
  ]) assert.ok(between(s.confidence, 0, 1), String(s.confidence));
});

test('confirm and dispute counts are of events, not of authors', () => {
  const s = sum(
    ev('a', 'S2'),
    ev('b', 'S2', { type: 'confirm', id: 'c1' }),
    ev('c', 'S2', { type: 'confirm', id: 'c2' }),
    ev('d', 'S0', { type: 'dispute', id: 'd1' }),
  );
  assert.equal(s.confirmCount, 2);
  assert.equal(s.disputeCount, 1);
});

test('a feature is stale only once every event has expired', () => {
  assert.ok(sum(ev('a', 'S3', { minutesAgo: 400 })).isStale);
  assert.ok(!sum(ev('a', 'S3', { minutesAgo: 400 }), ev('b', 'S3')).isStale);
});

test('history is newest first', () => {
  const s = sum(ev('a', 'S2', { minutesAgo: 30 }), ev('b', 'S2', { minutesAgo: 5 }), ev('c', 'S2', { minutesAgo: 60 }));
  const times = s.events.map((e) => e.timestampMs);
  assert.deepEqual(times, [...times].sort((x, y) => y - x));
});

test('an official ruling is not also counted inside the crowd it overrides', () => {
  const s = sum(ev('a', 'S3'), ev('kag', 'S0', { role: 'official' }));
  assert.ok(!s.isConflicted);
  assert.equal(s.severity, 'S0');
  assert.equal(s.bucket, 'official');
});

test('expired event within the 24h grace still renders as stale', () => {
  const s = sum(ev('a', 'S3', { minutesAgo: 400 }));
  assert.ok(s.isStale);
  assert.equal(s.severity, 'S3');
});

// ---- Hand-computed values, pinning the constants the phone's tests only bound ----
// Wilson lower bound with no dissent is 1 / (1 + z^2 / n), z = 1.44, n = summed weight.
const wilsonAlone = (n: number) => 1 / (1 + 1.44 ** 2 / n);

test('one resident report 5 min old: weight is decay only, confidence is Wilson of that weight', () => {
  const s = sum(ev('a', 'S2'));
  assert.ok(Math.abs(s.confidence - wilsonAlone(Math.exp(-5 / 240))) < 1e-9, String(s.confidence));
});

test('a responder counts 2.5x a resident (role weight is pinned)', () => {
  const s = sum(ev('a', 'S3'), ev('b', 'S3', { role: 'responder' }));
  assert.ok(Math.abs(s.confidence - wilsonAlone(3.5 * Math.exp(-5 / 360))) < 1e-9, String(s.confidence));
});

test('a reporter 900 m away counts at 0.4 (proximity band is pinned)', () => {
  const s = sum(ev('a', 'S3'), ev('b', 'S3', { lat: farLat, type: 'confirm' }));
  assert.ok(Math.abs(s.confidence - wilsonAlone(1.4 * Math.exp(-5 / 360))) < 1e-9, String(s.confidence));
});

// ---- OfficialOverrideTest (its own helper: fixed expiry, other anchor, official_status type) ----
const anchor2 = { lat: 18.1712, lon: 120.5934 };
function off(author: string, severity: string, o: { role?: string; minutesAgo?: number } = {}) {
  const role = o.role ?? 'resident';
  const minutesAgo = o.minutesAgo ?? 5;
  return {
    ...ev(author, severity, { role, minutesAgo, lat: anchor2.lat, lon: anchor2.lon, type: role === 'official' ? 'official_status' : 'flood_report', id: `${author}-${severity}-${minutesAgo}`, expiresAt: now + 60 * 60_000 }),
  };
}
const sum2 = (...events: ReturnType<typeof off>[]) => summarize(ref, events, now)!;

test('an official raising the severity applies immediately', () => {
  const s = sum2(off('res-1', 'S1'), off('res-2', 'S1'), off('kagawad-1', 'S3', { role: 'official', minutesAgo: 1 }));
  assert.equal(s.severity, 'S3');
  assert.equal(s.bucket, 'official');
  assert.ok(!s.pendingSecondOfficial);
});

test('an official clearing a road whose reports have decayed applies immediately', () => {
  const s = sum2(off('res-1', 'S1', { minutesAgo: 600 }), off('res-2', 'S1', { minutesAgo: 590 }), off('kagawad-1', 'S0', { role: 'official', minutesAgo: 1 }));
  assert.equal(s.severity, 'S0');
  assert.equal(s.bucket, 'official');
  assert.ok(!s.pendingSecondOfficial);
  assert.equal(s.contradictingCount, 0);
});

test('two residents still reporting worse do gate a clearance, however old the road is', () => {
  const s = sum2(off('res-1', 'S2', { minutesAgo: 90 }), off('res-2', 'S2', { minutesAgo: 85 }), off('kagawad-1', 'S0', { role: 'official', minutesAgo: 1 }));
  assert.equal(s.contradictingCount, 2);
  assert.ok(s.pendingSecondOfficial);
  assert.equal(s.severity, 'S2');
});

test('one official cannot clear a road two residents say is still impassable', () => {
  const s = sum2(off('res-1', 'S3', { minutesAgo: 4 }), off('res-2', 'S3', { minutesAgo: 6 }), off('kagawad-1', 'S0', { role: 'official', minutesAgo: 1 }));
  assert.equal(s.severity, 'S3');
  assert.ok(s.pendingSecondOfficial);
  assert.equal(s.officialSeverity, 'S0');
  assert.equal(s.contradictingCount, 2);
  assert.notEqual(s.bucket, 'official');
});

test('a second official agreeing releases the gate', () => {
  const base = [off('res-1', 'S3', { minutesAgo: 4 }), off('res-2', 'S3', { minutesAgo: 6 }), off('kagawad-1', 'S0', { role: 'official', minutesAgo: 2 })];
  assert.ok(sum2(...base).pendingSecondOfficial);
  const released = sum2(...base, off('kagawad-2', 'S0', { role: 'official', minutesAgo: 1 }));
  assert.equal(released.severity, 'S0');
  assert.equal(released.bucket, 'official');
  assert.ok(!released.pendingSecondOfficial);
});

test('the same official posting twice is still one official', () => {
  const s = sum2(
    off('res-1', 'S3', { minutesAgo: 4 }),
    off('res-2', 'S3', { minutesAgo: 6 }),
    off('kagawad-1', 'S0', { role: 'official', minutesAgo: 3 }),
    off('kagawad-1', 'S0', { role: 'official', minutesAgo: 1 }),
  );
  assert.ok(s.pendingSecondOfficial);
  assert.equal(s.severity, 'S3');
});

test('a lone dissenting resident does not gate a clearance', () => {
  const s = sum2(off('res-1', 'S3', { minutesAgo: 4 }), off('kagawad-1', 'S0', { role: 'official', minutesAgo: 1 }));
  assert.equal(s.severity, 'S0');
  assert.ok(!s.pendingSecondOfficial);
  assert.equal(s.contradictingCount, 1);
});

test("reversing another official's clearance takes only one official", () => {
  const s = sum2(
    off('res-1', 'S2', { minutesAgo: 20 }),
    off('res-2', 'S2', { minutesAgo: 18 }),
    off('kagawad-1', 'S0', { role: 'official', minutesAgo: 10 }),
    off('kagawad-2', 'S3', { role: 'official', minutesAgo: 1 }),
  );
  assert.equal(s.severity, 'S3');
  assert.equal(s.bucket, 'official');
  assert.ok(!s.pendingSecondOfficial);
});

test('an official status never deletes the resident reports underneath it', () => {
  const s = sum2(off('res-1', 'S3', { minutesAgo: 4 }), off('res-2', 'S3', { minutesAgo: 6 }), off('kagawad-1', 'S3', { role: 'official', minutesAgo: 1 }));
  assert.equal(s.events.length, 3);
});

test('with no official at all nothing about the crowd result changes', () => {
  const s = sum2(off('res-1', 'S2'), off('res-2', 'S2'));
  assert.equal(s.officialSeverity, null);
  assert.ok(!s.pendingSecondOfficial);
  assert.equal(s.contradictingCount, 0);
  assert.equal(s.severity, 'S2');
});

test('an SX conflict cannot be cleared by one official but can by two', () => {
  const conflict = [off('res-1', 'S3', { minutesAgo: 3 }), off('res-2', 'S3', { minutesAgo: 5 }), off('res-3', 'S0', { minutesAgo: 4 })];
  assert.ok(sum2(...conflict).isConflicted);
  const one = sum2(...conflict, off('kagawad-1', 'S0', { role: 'official', minutesAgo: 1 }));
  assert.ok(one.pendingSecondOfficial);
  assert.ok(one.isConflicted);
  const two = sum2(...conflict, off('kagawad-1', 'S0', { role: 'official', minutesAgo: 2 }), off('kagawad-2', 'S0', { role: 'official', minutesAgo: 1 }));
  assert.ok(!two.pendingSecondOfficial);
  assert.equal(two.severity, 'S0');
});

// ---- WithdrawTest ----
const wd = (author: string, minutesAgo: number) => ev(author, null, { type: 'flood_withdraw', minutesAgo });

test('withdrawing a lone report removes the feature', () => {
  assert.equal(summarize(ref, [ev('a', 'S3', { minutesAgo: 10 }), wd('a', 5)], now), null);
});

test('withdrawing one of two reports keeps the other and drops the withdrawn one', () => {
  const s = sum(ev('a', 'S3', { minutesAgo: 10 }), ev('b', 'S3', { minutesAgo: 8 }), wd('a', 5));
  const reports = s.events.filter((e) => e.type === 'flood_report');
  assert.equal(reports.length, 1);
  assert.equal(reports[0].authorId, 'b');
});

test("withdrawing also takes back the author's confirms", () => {
  const s = sum(ev('a', 'S3', { minutesAgo: 10 }), ev('b', 'S3', { type: 'confirm', minutesAgo: 8 }), wd('b', 5));
  assert.equal(s.confirmCount, 0);
});

test("someone else's withdrawal changes nothing", () => {
  const s = sum(ev('a', 'S3', { minutesAgo: 10 }), wd('intruder', 5));
  assert.equal(s.events.filter((e) => e.type === 'flood_report').length, 1);
});

test('a report filed after the withdrawal counts again', () => {
  assert.notEqual(summarize(ref, [ev('a', 'S3', { minutesAgo: 10 }), wd('a', 8), ev('a', 'S3', { minutesAgo: 2 })], now), null);
});

test('arrival order does not matter', () => {
  const a = ev('a', 'S3', { minutesAgo: 10 });
  const b = ev('b', 'S3', { minutesAgo: 8 });
  const w = wd('a', 5);
  const strip = (s: NonNullable<ReturnType<typeof summarize>>) => ({ ...s, events: [] });
  assert.deepEqual(strip(sum(a, b, w)), strip(sum(w, b, a)));
});

test('the timeline keeps the withdrawal but not the withdrawn report', () => {
  const s = sum(ev('a', 'S3', { minutesAgo: 10 }), ev('b', 'S3', { minutesAgo: 8 }), wd('a', 5));
  assert.ok(s.events.some((e) => e.type === 'flood_withdraw'));
  assert.ok(!s.events.some((e) => e.authorId === 'a' && e.type === 'flood_report'));
});

test("an official's own withdrawal removes their ruling", () => {
  const s = sum(ev('r', 'S3', { minutesAgo: 10 }), ev('o', 'S0', { role: 'official', type: 'official_status', minutesAgo: 8 }), wd('o', 5));
  assert.equal(s.officialSeverity, null);
});

console.log(`${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
