package com.macci.kaalerto.data

import android.content.Context
import kotlinx.serialization.json.Json

private const val SEED_ASSET = "seed_data.json"
private val seedJson = Json { ignoreUnknownKeys = true }

/**
 * Loads `assets/seed_data.json` into the event store, replacing any earlier copy, on every
 * cold start. Ported from passable-v0 (9798aac).
 *
 * Seed times are written as "N minutes ago", so they are only meaningful relative to when
 * they were loaded. Loading on first launch only let the demo map age in real time from
 * install: S0 decays with a 60-minute tau, so the cleared half of the SX pair dropped
 * under the 0.5 conflict threshold ~42 minutes after its own timestamp, and the SX
 * marker the demo opens became a plain S3 about 35 minutes after first launch. Clearing
 * app data to reset it also deletes the offline map pack. Reloading on each start means a
 * force-stop and relaunch always brings the demo map back as authored.
 *
 * Only `origin = 'seed'` rows are replaced ([EventDao.replaceSeeds]); a resident's own
 * reports and everything received over mesh or sync are never touched. That bends the
 * append-only rule on purpose: seeds are demo fixtures, not observations, and neither
 * transport carries them ([com.macci.kaalerto.sync.eventsToSync] and
 * [com.macci.kaalerto.mesh.relayable] both skip origin "seed").
 */
class SeedLoader(private val context: Context, private val repository: EventRepository) {
    suspend fun refresh() {
        val json = context.assets.open(SEED_ASSET).bufferedReader().use { it.readText() }
        val seedFile = seedJson.decodeFromString<SeedFile>(json)
        val now = System.currentTimeMillis()
        repository.replaceSeeds(seedFile.reports.map { it.toEvent(now) })
    }

    private fun SeedReport.toEvent(now: Long): Event {
        val timestampMs = now - timestampMinutesAgo * 60_000L
        return Event(
            id = id,
            type = type,
            lat = lat,
            lon = lon,
            featureRef = featureRef,
            severity = severity,
            waterLevel = waterLevel,
            authorId = authorId,
            authorName = authorName,
            authorRole = authorRole,
            timestampMs = timestampMs,
            expiresAt = timestampMs + ttlMinutes * 60_000L,
            origin = origin,
            hopCount = hopCount,
            note = note,
        )
    }
}
