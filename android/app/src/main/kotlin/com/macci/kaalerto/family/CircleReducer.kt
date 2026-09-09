package com.macci.kaalerto.family

import com.macci.kaalerto.data.Event

/** One circle member as the Family screen renders them. */
data class CircleMemberStatus(
    val authorId: String,
    val displayName: String,
    /** Null means "no check-in yet" — never rendered the same as a bad-news state. */
    val lastCheckInMs: Long?,
)

/**
 * Folds the shared event stream independently of the flood reducer (`data/Reducer.kt`),
 * the same shape as `sos/SosReducer.kt`/`identity/RoleReducer.kt`. [circle] is
 * `effectiveCircle(...)`'s output, not raw `CircleStore` — every paired member appears
 * here regardless of whether any check-in has arrived, the same "absence isn't the same
 * claim as presence" reasoning `map/RoleActionStrip.kt` already established for the
 * empty rescue queue.
 */
fun circleStatuses(allEvents: List<Event>, circle: List<CircleMember>): List<CircleMemberStatus> {
    val circleIds = circle.map { it.authorId }.toSet()
    val latestCheckIn: Map<String, Event> = allEvents
        .asSequence()
        .filter { it.type == TYPE_CHECKIN && it.authorId in circleIds }
        .groupBy { it.authorId }
        .mapValues { (_, events) -> events.maxBy { it.timestampMs } }

    return circle.map { member ->
        val event = latestCheckIn[member.authorId]
        CircleMemberStatus(
            authorId = member.authorId,
            // The event's own authorName is the ground truth for display, same rule as
            // everywhere else in the app — falls back to the locally-known name only
            // when nobody has checked in yet to supply a fresher one.
            displayName = event?.authorName ?: member.displayName,
            lastCheckInMs = event?.timestampMs,
        )
    }
}
