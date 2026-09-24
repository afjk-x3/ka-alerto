package com.macci.kaalerto.family

import com.macci.kaalerto.data.Event

/** One circle member as the Family screen renders them. */
data class CircleMember(val authorId: String, val displayName: String, val pairedAtMs: Long)

/** What a device resolves to for its own circle. [name] is null until this device
 * has seen the matching [TYPE_CIRCLE_CREATE] event — e.g. right after joining by
 * code, before the creator's event has arrived over mesh or Supabase. */
data class ResolvedCircle(
    val circleId: String,
    val name: String?,
    val members: List<CircleMember>,
)

private fun circleIdOf(event: Event): String? = when (event.type) {
    TYPE_CIRCLE_CREATE -> decodeCircleCreatePayload(event.payload)?.circleId
    TYPE_CIRCLE_JOIN -> decodeCircleJoinPayload(event.payload)?.circleId
    else -> null
}

/**
 * My circle: whichever of my own [TYPE_CIRCLE_CREATE]/[TYPE_CIRCLE_JOIN] events is
 * newest gives my `circleId` — switching circles is simply authoring a newer one, no
 * explicit "leave" needed. Membership is everyone else whose own newest create/join
 * event points at that same `circleId`. Pure fold, same shape as
 * `evac/EvacCentre.kt`'s `resolveCentres` — no persisted store, recomputed on every
 * call. See `notes/specs/2026-09-23-circle-create-join-redesign.md`.
 */
fun resolveCircle(allEvents: List<Event>, myAuthorId: String): ResolvedCircle? {
    val relevant = allEvents.filter { it.type == TYPE_CIRCLE_CREATE || it.type == TYPE_CIRCLE_JOIN }
    val latestPerAuthor = relevant.groupBy { it.authorId }
        .mapValues { (_, events) -> events.maxBy { it.timestampMs } }

    val myEvent = latestPerAuthor[myAuthorId] ?: return null
    val myCircleId = circleIdOf(myEvent) ?: return null

    // Earliest create wins the name, not latest — otherwise anyone holding the join
    // code could rename the circle by posting a newer circle_create for the same id.
    val name = relevant
        .filter { it.type == TYPE_CIRCLE_CREATE && circleIdOf(it) == myCircleId }
        .minByOrNull { it.timestampMs }
        ?.let { decodeCircleCreatePayload(it.payload)?.name }

    val members = latestPerAuthor.values
        .filter { it.authorId != myAuthorId && circleIdOf(it) == myCircleId }
        .map { CircleMember(authorId = it.authorId, displayName = it.authorName, pairedAtMs = it.timestampMs) }

    return ResolvedCircle(circleId = myCircleId, name = name, members = members)
}
