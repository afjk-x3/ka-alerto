// The map's opening view (src/lib/focus.ts). Run: npm run check
import assert from 'node:assert/strict';
import { initialFocus } from '../src/lib/focus.ts';

const now = 1_700_000_000_000;
const h = 3_600_000;
const north = { lat: 18.17, lon: 120.6 }; // Ilocos Norte
const south = { lat: 16.03, lon: 120.42 }; // Pangasinan, about 240 km away

type P = { id: string; lat: number; lon: number; ageH: number; sos?: 'open' | 'closed' };
const item = (p: P) => ({
  kind: p.sos ? 'sos' : 'report',
  id: p.id,
  lat: p.lat,
  lon: p.lon,
  updatedAtMs: now - p.ageH * h,
  closed: p.sos === 'closed',
}) as never;
const ids = (xs: { id: string }[]) => xs.map((x) => x.id).sort();

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

test('nothing to frame gives nothing', () => {
  assert.deepEqual(initialFocus([], now), []);
});

test('open SOS and the last 24 h are framed, older items are not', () => {
  const list = [
    item({ id: 'recent', ...north, ageH: 3 }),
    item({ id: 'old', ...south, ageH: 50 }),
    item({ id: 'sos', ...south, ageH: 70, sos: 'open' }),
  ];
  assert.deepEqual(ids(initialFocus(list, now)), ['recent', 'sos']);
});

test('a closed SOS is not urgent on its own', () => {
  const list = [item({ id: 'a', ...north, ageH: 60, sos: 'closed' }), item({ id: 'b', ...south, ageH: 90 })];
  assert.deepEqual(ids(initialFocus(list, now)), ['a']); // falls back to the newest item's area
});

test('with nothing recent, only the newest item and what is near it are framed', () => {
  const list = [
    item({ id: 'n1', ...north, ageH: 48 }),
    item({ id: 'n2', lat: 18.171, lon: 120.601, ageH: 60 }),
    item({ id: 's1', ...south, ageH: 72 }),
    item({ id: 's2', lat: 16.031, lon: 120.421, ageH: 80 }),
  ];
  assert.deepEqual(ids(initialFocus(list, now)), ['n1', 'n2']);
});

test('everything close together is framed together', () => {
  const list = [item({ id: 'a', ...north, ageH: 60 }), item({ id: 'b', lat: 18.18, lon: 120.61, ageH: 90 })];
  assert.deepEqual(ids(initialFocus(list, now)), ['a', 'b']);
});

console.log(`${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
