package com.macci.kaalerto.report

import android.content.Context
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.data.ttlMinutesFor
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.location.geohashEncode
import java.util.UUID

/**
 * Writes a new report straight to the local store — the whole point of FR-2.2
 * ("write to device storage... before attempting any transmission"). There is no
 * outbound queue yet (that's day 6+ mesh/day 12 SMS/day 13 server sync); this is only
 * the local half.
 */
suspend fun submitReport(
    context: Context,
    level: WaterLevelOption,
    severity: String,
    lat: Double,
    lon: Double,
    /** Set only when a photo was captured/picked — see report/ReportPhoto.kt. */
    photoHash: String? = null,
): String {
    val identity = LocalIdentity.getOrCreate(context)
    val now = System.currentTimeMillis()
    // No road-network graph exists yet, and BUILD_TASKS.md day 3 explicitly says to
    // skip snap-to-road — a geohash cell is the fallback docs/03-architecture.md's
    // own schema names, and it's what lets day 4's reducer group same-spot reports.
    val featureRef = geohashEncode(lat, lon)
    val event = Event(
        id = "local-${UUID.randomUUID()}",
        type = "flood_report",
        lat = lat,
        lon = lon,
        featureRef = featureRef,
        severity = severity,
        waterLevel = level.id,
        authorId = identity.authorId,
        authorName = identity.authorName,
        authorRole = identity.authorRole,
        timestampMs = now,
        expiresAt = now + ttlMinutesFor(severity) * 60_000L,
        origin = "local",
        hopCount = 0,
        note = null,
        payload = photoHash?.let { ReportPhotoPayload(it).encode() },
    )
    val repository = EventRepository(KaAlertoDatabase.getInstance(context).eventDao())
    repository.insert(event)
    return featureRef
}
