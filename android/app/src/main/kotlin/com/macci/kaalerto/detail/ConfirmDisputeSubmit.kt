package com.macci.kaalerto.detail

import android.content.Context
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.data.severityDown
import com.macci.kaalerto.data.severityUp
import com.macci.kaalerto.data.ttlMinutesFor
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.location.fetchCurrentLocation
import java.util.UUID

enum class DisputeReason { CLEARED_NOW, WORSE, SHALLOWER, WRONG_LOCATION }

/** Confirming endorses whatever severity is currently displayed for the feature. */
suspend fun submitConfirm(context: Context, featureRef: String, currentSeverity: String) {
    submit(context, featureRef, type = "confirm", severity = currentSeverity, disputeReason = null)
}

/**
 * Each follow-up reason implies a severity relative to what's currently displayed —
 * BUILD_TASKS.md day 4's "dispute action with follow-up (cleared now / worse /
 * shallower / wrong location)". Wrong-location carries no severity: it's a
 * data-quality flag, not a reading, so the reducer excludes it from weighting.
 */
suspend fun submitDispute(context: Context, featureRef: String, currentSeverity: String, reason: DisputeReason) {
    val severity = when (reason) {
        DisputeReason.CLEARED_NOW -> "S0"
        DisputeReason.WORSE -> severityUp(currentSeverity)
        DisputeReason.SHALLOWER -> severityDown(currentSeverity)
        DisputeReason.WRONG_LOCATION -> null
    }
    submit(context, featureRef, type = "dispute", severity = severity, disputeReason = reason.name.lowercase())
}

private suspend fun submit(context: Context, featureRef: String, type: String, severity: String?, disputeReason: String?) {
    val identity = LocalIdentity.getOrCreate(context)
    // No fix -> (0,0), thousands of km from the demo area, which the reducer's
    // haversine distance naturally classifies as "remote, no usable fix" (proximity
    // 0.2) per docs/03-architecture.md §5.2 — the same outcome a real no-fix case
    // should have, without needing a separate "unknown location" signal in the schema.
    val location = fetchCurrentLocation(context)
    val now = System.currentTimeMillis()
    val event = Event(
        id = "local-${UUID.randomUUID()}",
        type = type,
        lat = location?.latitude ?: 0.0,
        lon = location?.longitude ?: 0.0,
        featureRef = featureRef,
        severity = severity,
        waterLevel = null,
        authorId = identity.authorId,
        authorName = identity.authorName,
        authorRole = identity.authorRole,
        timestampMs = now,
        expiresAt = now + ttlMinutesFor(severity) * 60_000L,
        origin = "local",
        hopCount = 0,
        note = null,
        disputeReason = disputeReason,
    )
    EventRepository(KaAlertoDatabase.getInstance(context).eventDao()).insert(event)
}
