// The phone's EvacCentresTest cases (android/app/src/test/.../evac/EvacCentresTest.kt) and PsgcTest search
// cases, run against the dashboard's ports (src/lib/evac.ts, src/lib/psgc.ts). Run: npm run check
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { buildEvacStates, canManage, resolveCentres, sameMunicipality, CENTRES } from '../src/lib/evac.ts';
import { makePsgc } from '../src/lib/psgc.ts';

const now = 1_700_000_000_000;
const sanNicolas = 'San Nicolas, Ilocos Norte';
const laoag = 'Laoag City, Ilocos Norte';
const bundledId = CENTRES[0].id;

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

type Payload = Record<string, unknown>;
const event = (id: string, type: string, payload: Payload, minutesAgo: number, extra: Payload = {}) =>
  ({
    id,
    type,
    lat: 0,
    lon: 0,
    featureRef: null,
    severity: null,
    waterLevel: null,
    authorId: 'off-1',
    authorName: 'Kagawad A1B2',
    authorRole: 'official',
    timestampMs: now - minutesAgo * 60_000,
    expiresAt: now + 3_600_000,
    origin: 'local',
    hopCount: 0,
    note: null,
    disputeReason: null,
    payload: JSON.stringify(payload),
    ...extra,
  }) as never;

const centre = (id: string, o: { minutesAgo?: number; name?: string; municipality?: string; removed?: boolean } = {}) => {
  const minutesAgo = o.minutesAgo ?? 10;
  const municipality = o.municipality ?? sanNicolas;
  const name = o.name ?? 'Barangay Hall';
  return event(`c-${id}-${minutesAgo}-${municipality}-${o.removed ?? false}-${name}`, 'evac_centre', {
    centreId: id, name, kind: 'barangay_hall', lat: 18.17, lon: 120.6, municipality, barangay: 'Brgy. Sotto', removed: o.removed ?? false,
  }, minutesAgo);
};

const status = (centreId: string, s: string, o: { minutesAgo?: number; municipality?: string | null; occupancy?: number | null } = {}) => {
  const minutesAgo = o.minutesAgo ?? 5;
  const municipality = o.municipality === undefined ? sanNicolas : o.municipality;
  return event(`s-${centreId}-${minutesAgo}-${s}-${municipality}`, 'evac_status', { centreId, status: s, occupancy: o.occupancy ?? null, municipality }, minutesAgo);
};

const states = (...events: never[]) => buildEvacStates(events, now);
const byId = (list: ReturnType<typeof states>, id: string) => list.find((s) => s.centre.id === id);

// ---- adding ----
test('with no events only the bundled shelters exist, all not open yet', () => {
  const list = states();
  assert.deepEqual(list.map((s) => s.centre.id).sort(), CENTRES.map((c) => c.id).sort());
  assert.ok(list.every((s) => s.status === 'not_open'));
});

test('an added shelter appears with its municipality and barangay, closed by default', () => {
  const s = byId(states(centre('evac-new')), 'evac-new')!;
  assert.equal(s.centre.name, 'Barangay Hall');
  assert.equal(s.centre.municipality, sanNicolas);
  assert.equal(s.centre.barangay, 'Brgy. Sotto');
  assert.ok(s.centre.custom);
  assert.equal(s.status, 'not_open');
});

test('a later event from the same municipality edits it, the latest wins', () => {
  const s = byId(states(centre('evac-new', { minutesAgo: 20, name: 'Old name' }), centre('evac-new', { minutesAgo: 5, name: 'New name' })), 'evac-new')!;
  assert.equal(s.centre.name, 'New name');
});

test('arrival order does not change the result', () => {
  const a = centre('evac-new', { minutesAgo: 20, name: 'Old name' });
  const b = centre('evac-new', { minutesAgo: 5, name: 'New name' });
  assert.deepEqual(states(a, b).map((s) => s.centre), states(b, a).map((s) => s.centre));
});

test('another municipality cannot rewrite or remove a shelter it did not create', () => {
  const created = centre('evac-new', { minutesAgo: 20, name: 'Original' });
  const hijack = centre('evac-new', { minutesAgo: 5, name: 'Hijacked', municipality: laoag });
  const wipe = centre('evac-new', { minutesAgo: 4, municipality: laoag, removed: true });
  assert.equal(byId(states(created, hijack, wipe), 'evac-new')!.centre.name, 'Original');
});

test('the same municipality spelled differently is still the same municipality', () => {
  const created = centre('evac-new', { minutesAgo: 20, name: 'Original' });
  const edit = centre('evac-new', { minutesAgo: 5, name: 'Edited', municipality: '  SAN  NICOLAS, ilocos norte ' });
  assert.equal(byId(states(created, edit), 'evac-new')!.centre.name, 'Edited');
});

// ---- removing ----
test('a removed shelter disappears', () => {
  assert.equal(byId(states(centre('evac-new', { minutesAgo: 20 }), centre('evac-new', { minutesAgo: 5, removed: true })), 'evac-new'), undefined);
});

test('a bundled shelter cannot be removed or replaced by an event', () => {
  const list = states(centre(bundledId, { removed: true }), centre(bundledId, { name: 'Renamed' }));
  assert.equal(byId(list, bundledId)!.centre.name, CENTRES[0].name);
});

test('an event with no name or municipality is ignored', () => {
  assert.equal(byId(states(centre('evac-x', { name: '  ' })), 'evac-x'), undefined);
  assert.equal(byId(states(centre('evac-y', { municipality: '' })), 'evac-y'), undefined);
});

// ---- opening and closing, scoped ----
test("an official of the shelter's municipality can open it", () => {
  const s = byId(states(centre('evac-new'), status('evac-new', 'accepting', { occupancy: 12 })), 'evac-new')!;
  assert.equal(s.status, 'accepting');
  assert.equal(s.occupancy, 12);
});

test('an update stamped with another municipality is ignored', () => {
  assert.equal(byId(states(centre('evac-new'), status('evac-new', 'accepting', { municipality: laoag })), 'evac-new')!.status, 'not_open');
});

test('an older update with no municipality is still accepted', () => {
  assert.equal(byId(states(status(bundledId, 'accepting', { municipality: null })), bundledId)!.status, 'accepting');
});

test('a status for a shelter that does not exist, or was removed, changes nothing', () => {
  const removed = states(centre('evac-new', { minutesAgo: 30 }), centre('evac-new', { minutesAgo: 20, removed: true }), status('evac-new', 'accepting'));
  assert.equal(byId(removed, 'evac-new'), undefined);
  assert.equal(states(status('evac-ghost', 'accepting')).length, CENTRES.length);
});

test('close then open again ends open, the latest update wins', () => {
  const list = states(status(bundledId, 'accepting', { minutesAgo: 30 }), status(bundledId, 'not_open', { minutesAgo: 20 }), status(bundledId, 'accepting', { minutesAgo: 10 }));
  assert.equal(byId(list, bundledId)!.status, 'accepting');
});

test('an update past its own expiry is ignored (Supabase never purges)', () => {
  const stale = event('s-old', 'evac_status', { centreId: bundledId, status: 'accepting', municipality: sanNicolas }, 60 * 30, { expiresAt: now - 1000 });
  assert.equal(byId(states(stale), bundledId)!.status, 'not_open');
});

// ---- who may manage ----
test("an official manages only their own municipality's shelters, and none if theirs is unset", () => {
  const c = CENTRES[0];
  assert.ok(canManage(sanNicolas, c));
  assert.ok(canManage(' san nicolas, ilocos NORTE', c));
  assert.ok(!canManage(laoag, c));
  assert.ok(!canManage('', c));
  assert.ok(!canManage(null, c));
  assert.ok(sameMunicipality('A', ' a '));
});

test('resolveCentres keeps the bundled four first', () => {
  const list = resolveCentres([centre('evac-new')] as never[]);
  assert.deepEqual(list.slice(0, CENTRES.length).map((c) => c.id), CENTRES.map((c) => c.id));
  assert.equal(list.length, CENTRES.length + 1);
});

// ---- PSGC search (PsgcTest) ----
const psgc = makePsgc(JSON.parse(readFileSync(new URL('../src/data/psgc.json', import.meta.url), 'utf8')));

test('the three San Nicolas stay apart by province', () => {
  assert.deepEqual(new Set(psgc.search('san nicolas', 20).filter((l) => l.startsWith('San Nicolas,'))), new Set(['San Nicolas, Batangas', 'San Nicolas, Ilocos Norte', 'San Nicolas, Pangasinan']));
});

test('search finds by name or name and province, starting-with first; find ignores case and spacing', () => {
  assert.equal(psgc.search('mapand')[0], 'Mapandan, Pangasinan');
  assert.equal(psgc.search('san nicolas ilocos')[0], 'San Nicolas, Ilocos Norte');
  assert.equal(psgc.find('  MAPANDAN,   pangasinan ')?.label, 'Mapandan, Pangasinan');
  assert.equal(psgc.find('Mapandan'), undefined);
  assert.deepEqual(psgc.search(''), []);
  assert.deepEqual(psgc.search('zzzzzz'), []);
});

test("barangay search lists the chosen municipality's barangays and drops the Pob marker", () => {
  assert.ok(psgc.barangays('San Nicolas, Ilocos Norte').includes('Brgy. San Juan Bautista'));
  assert.deepEqual(psgc.searchBarangays('San Nicolas, Ilocos Norte', 'juan'), ['Brgy. San Juan Bautista']);
  assert.equal(psgc.searchBarangays('San Nicolas, Ilocos Norte', '').length, 8);
  assert.equal(psgc.searchBarangays('San Nicolas, Ilocos Norte', 'Brgy').length, 8);
  assert.deepEqual(psgc.searchBarangays('San Nicolas, Ilocos Norte', 'Brgy. san juan'), ['Brgy. San Juan Bautista']);
  assert.deepEqual(psgc.barangays('Not A Place, Nowhere'), []);
});

console.log(`${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
