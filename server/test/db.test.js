'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');
const { openDatabase } = require('../src/db');

// A real temp file, not ':memory:' — this test needs to reopen the same database from a
// second connection to inspect a raw column, and two ':memory:' connections never share
// state (server.test.js's own comment notes the same limitation).
test('insertEvent stores expiresAt in the indexed expires_at_ms column, not just the payload', () => {
  const tmpPath = path.join(os.tmpdir(), `kaalerto-db-test-${Date.now()}-${Math.random().toString(36).slice(2)}.db`);
  const db = openDatabase(tmpPath);

  db.insertEvent({
    id: 'test-expiry-event',
    type: 'flood_report',
    lat: 18.17,
    lon: 120.6,
    timestampMs: 1000,
    expiresAt: 5000,
  });
  db.close();

  const raw = new DatabaseSync(tmpPath);
  const row = raw.prepare('SELECT expires_at_ms FROM events WHERE id = ?').get('test-expiry-event');
  raw.close();
  fs.unlinkSync(tmpPath);

  assert.equal(row.expires_at_ms, 5000);
});
