'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

// Must be set before requiring server.js — DB_PATH is read at module load time.
process.env.KAALERTO_DB_PATH = ':memory:';

const { app } = require('../src/server');

// A fresh in-memory DB per test run (not per test — node:sqlite has no easy reset here),
// so tests share state within one file run and are written to not collide: distinct
// event IDs and a bbox big enough to be unambiguous.
let server;
let baseUrl;

test.before(() => {
  server = app.listen(0);
  const { port } = server.address();
  baseUrl = `http://127.0.0.1:${port}`;
});

test.after(() => {
  server.close();
});

// Reports along Sotto Street, well inside DemoArea.bounds.
const EVENT_A = {
  id: 'test-event-a',
  type: 'flood_report',
  lat: 18.1709,
  lon: 120.6058,
  severity: 'S2',
  authorId: 'test-author-1',
  timestampMs: Date.now(),
};
const EVENT_B = {
  id: 'test-event-b',
  type: 'flood_report',
  lat: 18.171,
  lon: 120.606,
  severity: 'S1',
  authorId: 'test-author-2',
  timestampMs: Date.now(),
};
const DEMO_BBOX = '120.5990,18.1660,120.6130,18.1760';
const ELSEWHERE_BBOX = '121.0,14.0,121.1,14.1'; // Manila-ish, nowhere near the demo area

test('GET /health reports ok and a numeric cursor', async () => {
  const res = await fetch(`${baseUrl}/health`);
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.ok, true);
  assert.equal(typeof body.cursor, 'number');
});

test('POST /events/batch requires a body shaped { events: [...] }', async () => {
  const res = await fetch(`${baseUrl}/events/batch`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ notEvents: [] }),
  });
  assert.equal(res.status, 400);
});

test('POST /events/batch accepts a new event and rejects an invalid one in the same batch', async () => {
  const invalid = { id: 'test-event-invalid', type: 'flood_report', lat: 999, lon: 120.6 };
  const res = await fetch(`${baseUrl}/events/batch`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ events: [EVENT_A, invalid] }),
  });
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.results.length, 2);
  assert.equal(body.results[0].status, 'accepted');
  assert.equal(body.results[1].status, 'rejected');
  assert.equal(body.results[1].reason, 'invalid lat');
});

test('POST /events/batch is idempotent: re-posting the same id is a duplicate, not an error', async () => {
  const res = await fetch(`${baseUrl}/events/batch`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ events: [EVENT_A] }),
  });
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.results[0].status, 'duplicate');
});

test('POST /events/batch over the size cap is rejected with 413', async () => {
  const events = Array.from({ length: 501 }, (_, i) => ({
    id: `overflow-${i}`, type: 'flood_report', lat: 18.17, lon: 120.6,
  }));
  const res = await fetch(`${baseUrl}/events/batch`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ events }),
  });
  assert.equal(res.status, 413);
});

test('GET /events requires a bbox', async () => {
  const res = await fetch(`${baseUrl}/events`);
  assert.equal(res.status, 400);
});

test('GET /events returns events inside the requested bbox and excludes ones outside it', async () => {
  await fetch(`${baseUrl}/events/batch`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ events: [EVENT_B] }),
  });

  const inBox = await fetch(`${baseUrl}/events?bbox=${DEMO_BBOX}&since=0`);
  const inBoxBody = await inBox.json();
  const ids = inBoxBody.events.map((e) => e.id);
  assert.ok(ids.includes('test-event-a'));
  assert.ok(ids.includes('test-event-b'));

  const outOfBox = await fetch(`${baseUrl}/events?bbox=${ELSEWHERE_BBOX}&since=0`);
  const outOfBoxBody = await outOfBox.json();
  assert.equal(outOfBoxBody.events.length, 0);
});

test('GET /events with since=<cursor> returns only events after that cursor, in order', async () => {
  const first = await fetch(`${baseUrl}/events?bbox=${DEMO_BBOX}&since=0&limit=1`);
  const firstBody = await first.json();
  assert.equal(firstBody.events.length, 1);
  assert.ok(firstBody.nextCursor > 0);

  const second = await fetch(`${baseUrl}/events?bbox=${DEMO_BBOX}&since=${firstBody.nextCursor}`);
  const secondBody = await second.json();
  assert.ok(secondBody.events.length >= 1);
  // The two pages must not overlap.
  const firstIds = new Set(firstBody.events.map((e) => e.id));
  for (const e of secondBody.events) assert.ok(!firstIds.has(e.id));
});

test('GET /events rejects a malformed bbox', async () => {
  const res = await fetch(`${baseUrl}/events?bbox=not,a,bbox`);
  assert.equal(res.status, 400);
});
