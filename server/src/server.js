'use strict';

const express = require('express');
const path = require('node:path');
const { openDatabase } = require('./db');

const PORT = process.env.PORT || 3000;
const DB_PATH = process.env.KAALERTO_DB_PATH || path.join(__dirname, '..', 'kaalerto.db');

// Batches and pull pages are capped so one client can't force a huge scan or a huge
// write on a box that is also meant to run on the demo laptop.
const MAX_BATCH_SIZE = 500;
const DEFAULT_PULL_LIMIT = 200;
const MAX_PULL_LIMIT = 1000;

const db = openDatabase(DB_PATH);
const app = express();

app.use(express.json({ limit: '2mb' }));

// Hand-rolled rather than the `cors` package: CLAUDE.md is explicit that Express is the
// only npm dependency this server takes on.
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  if (req.method === 'OPTIONS') {
    res.sendStatus(204);
    return;
  }
  next();
});

app.get('/health', (req, res) => {
  res.json({ ok: true, cursor: db.currentCursor() });
});

function isFiniteNumber(value) {
  return typeof value === 'number' && Number.isFinite(value);
}

/**
 * A minimal shape check, not a schema validator: id/type/lat/lon are what the server
 * actually needs to store and query on. Everything else round-trips through `payload`
 * untouched, so a field this server doesn't know about is never silently dropped.
 */
function validateEvent(event) {
  if (typeof event !== 'object' || event === null) return 'not an object';
  if (typeof event.id !== 'string' || event.id.length === 0) return 'missing id';
  if (typeof event.type !== 'string' || event.type.length === 0) return 'missing type';
  if (!isFiniteNumber(event.lat) || event.lat < -90 || event.lat > 90) return 'invalid lat';
  if (!isFiniteNumber(event.lon) || event.lon < -180 || event.lon > 180) return 'invalid lon';
  return null;
}

/**
 * POST /events/batch — push, device to server.
 *
 * Idempotent on event ID: re-posting is free (docs/03-architecture.md §392). Carry-forward
 * matters here — a device that relayed events it never authored uploads all of them on
 * reconnect, and this endpoint does not care who authored what it receives.
 */
app.post('/events/batch', (req, res) => {
  const events = req.body?.events;
  if (!Array.isArray(events)) {
    res.status(400).json({ error: 'body must be { events: [...] }' });
    return;
  }
  if (events.length > MAX_BATCH_SIZE) {
    res.status(413).json({ error: `batch exceeds ${MAX_BATCH_SIZE} events` });
    return;
  }

  const results = events.map((event) => {
    const reason = validateEvent(event);
    if (reason) {
      return { id: typeof event?.id === 'string' ? event.id : null, status: 'rejected', reason };
    }
    const status = db.insertEvent(event);
    return { id: event.id, status };
  });

  // docs/03-architecture.md §392 describes returning "the server's current cursor for
  // the device's regions" (plural, per-region). This demo has exactly one region and no
  // per-device region tracking, so it returns one global cursor instead. Revisit if a
  // second demo area is ever added.
  res.json({ results, cursor: db.currentCursor() });
});

/**
 * GET /events?bbox=minLon,minLat,maxLon,maxLat&since=<cursor>&limit=<n> — pull, server
 * to device (docs/03-architecture.md §393). Bbox is required: the point of scoping pulls
 * is that a device only ever downloads its own area.
 */
app.get('/events', (req, res) => {
  const bboxParam = req.query.bbox;
  if (typeof bboxParam !== 'string') {
    res.status(400).json({ error: 'bbox query param is required: minLon,minLat,maxLon,maxLat' });
    return;
  }

  const parts = bboxParam.split(',').map(Number);
  if (parts.length !== 4 || parts.some((n) => !Number.isFinite(n))) {
    res.status(400).json({ error: 'bbox must be four comma-separated numbers: minLon,minLat,maxLon,maxLat' });
    return;
  }
  const [minLon, minLat, maxLon, maxLat] = parts;
  if (minLon > maxLon || minLat > maxLat) {
    res.status(400).json({ error: 'bbox min must not exceed max' });
    return;
  }

  const since = Number(req.query.since ?? 0);
  if (!Number.isFinite(since) || since < 0) {
    res.status(400).json({ error: 'since must be a non-negative number' });
    return;
  }

  const requestedLimit = Number(req.query.limit ?? DEFAULT_PULL_LIMIT);
  const limit = Number.isFinite(requestedLimit)
    ? Math.min(Math.max(1, Math.trunc(requestedLimit)), MAX_PULL_LIMIT)
    : DEFAULT_PULL_LIMIT;

  const result = db.selectEventsSince({ since, minLon, minLat, maxLon, maxLat, limit });
  res.json(result);
});

function start() {
  const server = app.listen(PORT, () => {
    console.log(`KaAlerto server listening on :${PORT} (db: ${DB_PATH})`);
  });

  const shutdown = () => {
    server.close(() => {
      db.close();
      process.exit(0);
    });
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);

  return server;
}

if (require.main === module) {
  start();
}

module.exports = { app, start };
