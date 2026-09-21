package com.macci.kaalerto.data

import com.macci.kaalerto.identity.LocalIdentity

const val TYPE_FLOOD_WITHDRAW = "flood_withdraw"

/** Long enough that a withdrawal of a very short-lived report still outlives a slow relay. */
private const val MIN_WITHDRAW_TTL_MS = 60 * 60_000L

/**
 * "I take back what I said about this spot." An event, not a deletion: the log stays
 * append-only and every device folds the same withdrawal the same way (NFR-4).
 *
 * It carries the feature's ref and the author's id, and nothing else: [Reducer] drops
 * this author's earlier events on the feature and ignores anything the author did
 * before it, so only the author can withdraw their own say. Location is the null island
 * sentinel, as for role events — the anchor comes from reports only.
 *
 * It expires no earlier than the latest event it cancels; otherwise a device that gets
 * both late could bring the report back once the withdrawal had lapsed.
 */
fun withdrawEvent(
    author: LocalIdentity.Identity,
    featureRef: String,
    allEvents: List<Event>,
    nowMs: Long,
): Event {
    val latestExpiry = allEvents
        .filter { it.featureRef == featureRef && it.authorId == author.authorId }
        .maxOfOrNull { it.expiresAt } ?: 0L
    return Event(
        id = "withdraw-${java.util.UUID.randomUUID()}",
        type = TYPE_FLOOD_WITHDRAW,
        lat = 0.0,
        lon = 0.0,
        featureRef = featureRef,
        severity = null,
        waterLevel = null,
        authorId = author.authorId,
        authorName = author.authorName,
        authorRole = author.authorRole,
        timestampMs = nowMs,
        expiresAt = maxOf(latestExpiry, nowMs + MIN_WITHDRAW_TTL_MS),
        origin = "local",
        hopCount = 0,
        note = null,
    )
}

/**
 * The events that still count for one feature: each author's events newer than that
 * author's latest withdrawal, and never the withdrawals themselves. Re-reporting after a
 * withdrawal works because the new report is newer than it.
 */
fun liveEvents(featureEvents: List<Event>): List<Event> {
    val withdrawnAt = featureEvents
        .filter { it.type == TYPE_FLOOD_WITHDRAW }
        .groupBy { it.authorId }
        .mapValues { (_, w) -> w.maxOf { it.timestampMs } }
    return featureEvents.filter {
        it.type != TYPE_FLOOD_WITHDRAW && it.timestampMs > (withdrawnAt[it.authorId] ?: Long.MIN_VALUE)
    }
}

/** Whether this device still has something on the feature to take back. */
fun canWithdraw(summary: FeatureSummary, myAuthorId: String): Boolean =
    summary.events.any { it.authorId == myAuthorId && it.type != TYPE_FLOOD_WITHDRAW }
