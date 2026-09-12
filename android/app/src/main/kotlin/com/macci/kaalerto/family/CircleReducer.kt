package com.macci.kaalerto.family

import com.macci.kaalerto.data.Event

/** How a check-in event reached this device. */
enum class DeliveryMethod {
    /** Via internet (server sync) */
    INTERNET,
    /** Via SMS gateway */
    SMS,
    /** Via Bluetooth/WiFi Direct mesh relay */
    MESH,
    /** Unknown / not available yet */
    UNKNOWN,
}

/** One circle member as the Family screen renders them. */
data class CircleMemberStatus(
    val authorId: String,
    val displayName: String,
    /** Null means "no check-in yet" — never rendered the same as a bad-news state. */
    val lastCheckInMs: Long?,
    /** How the latest check-in (if any) was delivered to this device. */
    val deliveryMethod: DeliveryMethod = DeliveryMethod.UNKNOWN,
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
        val deliveryMethod = when (event?.origin) {
            "server" -> DeliveryMethod.INTERNET
            "sms" -> DeliveryMethod.SMS
            "mesh" -> DeliveryMethod.MESH
            else -> DeliveryMethod.UNKNOWN
        }
        CircleMemberStatus(
            authorId = member.authorId,
            // The event's own authorName is the ground truth for display, same rule as
            // everywhere else in the app — falls back to the locally-known name only
            // when nobody has checked in yet to supply a fresher one.
            displayName = event?.authorName ?: member.displayName,
            lastCheckInMs = event?.timestampMs,
            deliveryMethod = deliveryMethod,
        )
    }
}

/**
 * This device's own most recent "Ligtas ako" — deliberately separate from
 * [circleStatuses], which only ever covers [CircleMember]s from [effectiveCircle], and
 * [effectiveCircle] explicitly excludes `myAuthorId` from its own result (see that
 * function's own filter). `family/FamilyCircleScreen.kt` used to derive "my" status by
 * picking `statuses.firstOrNull { it.lastCheckInMs != null }` — the first *other* member
 * who happened to have checked in, silently mislabelled as the viewer's own status the
 * moment any circle member had a more recent check-in than the viewer did. This reads
 * this device's own [TYPE_CHECKIN] events directly instead, so there is nothing to
 * confuse it with.
 */
fun myLastCheckInMs(allEvents: List<Event>, myAuthorId: String): Long? =
    allEvents.filter { it.type == TYPE_CHECKIN && it.authorId == myAuthorId }
        .maxOfOrNull { it.timestampMs }
