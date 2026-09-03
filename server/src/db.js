'use strict';

const { DatabaseSync } = require('node:sqlite');

/**
 * The server DB is an aggregate, not the authority — every device computes its own map
 * from its own event store and would arrive at the same answer with the server switched
 * off (docs/03-architecture.md). This file only needs to store what arrives and answer
 * "what's new in this box since cursor X" fast enough to matter on a bad connection.
 *
 * `seq` is the cursor. It is a server-assigned, strictly monotonic counter — NOT
 * timestamp_ms, which is client-supplied, cannot be trusted for ordering, and can
 * collide across events. Pagination and "since" always compare against seq.
 */
function openDatabase(path) {
  const db = new DatabaseSync(path);

  db.exec(`
    CREATE TABLE IF NOT EXISTS events (
      seq           INTEGER PRIMARY KEY AUTOINCREMENT,
      id            TEXT UNIQUE NOT NULL,
      type          TEXT NOT NULL,
      lat           REAL NOT NULL,
      lon           REAL NOT NULL,
      feature_ref   TEXT,
      severity      TEXT,
      water_level   TEXT,
      author_id     TEXT,
      author_name   TEXT,
      author_role   TEXT,
      timestamp_ms  INTEGER,
      expires_at_ms INTEGER,
      origin        TEXT,
      hop_count     INTEGER NOT NULL DEFAULT 0,
      payload       TEXT NOT NULL,
      received_at_ms INTEGER NOT NULL
    );

    -- Skip R*Tree: at hackathon scale a plain indexed BETWEEN query over lat/lon is
    -- plenty, same call made for the Android local DB (BUILD_TASKS.md day 2).
    CREATE INDEX IF NOT EXISTS idx_events_lat ON events(lat);
    CREATE INDEX IF NOT EXISTS idx_events_lon ON events(lon);
  `);

  const insertStmt = db.prepare(`
    INSERT OR IGNORE INTO events (
      id, type, lat, lon, feature_ref, severity, water_level,
      author_id, author_name, author_role, timestamp_ms, expires_at_ms,
      origin, hop_count, payload, received_at_ms
    ) VALUES (
      @id, @type, @lat, @lon, @feature_ref, @severity, @water_level,
      @author_id, @author_name, @author_role, @timestamp_ms, @expires_at_ms,
      @origin, @hop_count, @payload, @received_at_ms
    )
  `);

  const selectSinceStmt = db.prepare(`
    SELECT * FROM events
    WHERE seq > @since
      AND lon BETWEEN @minLon AND @maxLon
      AND lat BETWEEN @minLat AND @maxLat
    ORDER BY seq ASC
    LIMIT @limit
  `);

  const maxSeqStmt = db.prepare(`SELECT COALESCE(MAX(seq), 0) AS maxSeq FROM events`);

  return {
    /**
     * Idempotent insert. Re-posting the same event ID is free — `INSERT OR IGNORE`
     * against the UNIQUE constraint on `id` makes duplicate delivery over server, mesh
     * and SMS harmless, per the dedupe-by-content-hash rule (docs/03-architecture.md §222).
     *
     * Returns 'accepted' or 'duplicate'. There is no signature or role verification here
     * — this hackathon build has no crypto and no auth (BUILD_TASKS.md ground rule 4);
     * the server trusts client-supplied event content. Real signing is designed, not built.
     */
    insertEvent(event) {
      const result = insertStmt.run({
        id: event.id,
        type: event.type,
        lat: event.lat,
        lon: event.lon,
        feature_ref: event.featureRef ?? null,
        severity: event.severity ?? null,
        water_level: event.waterLevel ?? null,
        author_id: event.authorId ?? null,
        author_name: event.authorName ?? null,
        author_role: event.authorRole ?? null,
        timestamp_ms: event.timestampMs ?? null,
        expires_at_ms: event.expiresAtMs ?? null,
        origin: event.origin ?? null,
        hop_count: event.hopCount ?? 0,
        payload: JSON.stringify(event),
        received_at_ms: Date.now(),
      });
      return result.changes > 0 ? 'accepted' : 'duplicate';
    },

    /**
     * Delta pull: everything after `since` inside the bounding box, oldest first.
     * `hasMore` is a cheap heuristic (exactly `limit` rows came back) rather than a
     * second COUNT query — good enough for a client that just calls again with the
     * returned nextCursor, which is the only way this is actually used.
     */
    selectEventsSince({ since, minLon, minLat, maxLon, maxLat, limit }) {
      const rows = selectSinceStmt.all({ since, minLon, minLat, maxLon, maxLat, limit });
      const events = rows.map((row) => JSON.parse(row.payload));
      const nextCursor = rows.length > 0 ? rows[rows.length - 1].seq : since;
      return { events, nextCursor, hasMore: rows.length === limit };
    },

    currentCursor() {
      return maxSeqStmt.get().maxSeq;
    },

    close() {
      db.close();
    },
  };
}

module.exports = { openDatabase };
