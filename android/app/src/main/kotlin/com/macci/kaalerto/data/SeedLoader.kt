package com.macci.kaalerto.data

import android.content.Context
import kotlinx.serialization.json.Json

private const val SEED_ASSET = "seed_data.json"
private val seedJson = Json { ignoreUnknownKeys = true }

/**
 * Loads `assets/seed_data.json` into the event store on first launch only — an
 * already-seeded device must not re-import fixtures on every cold start.
 */
class SeedLoader(private val context: Context, private val repository: EventRepository) {
    suspend fun loadIfEmpty() {
        if (!repository.isEmpty()) return

        val json = context.assets.open(SEED_ASSET).bufferedReader().use { it.readText() }
        val seedFile = seedJson.decodeFromString<SeedFile>(json)
        val now = System.currentTimeMillis()
        repository.insert(seedFile.reports.map { it.toEvent(now) })
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
