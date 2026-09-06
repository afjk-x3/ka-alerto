package com.macci.kaalerto.identity

import com.macci.kaalerto.data.Event

/** A roster seat that somebody has claimed, and who won it. */
data class SeatHolder(
    val seatId: String,
    val seatTitle: String,
    val authorId: String,
    val authorName: String,
    val sinceMs: Long,
    /**
     * Two or more devices claimed this seat. The earlier claim still holds it — a fold
     * has to return one answer — but nothing here can tell which claim was honest, so
     * the disagreement is surfaced rather than resolved. Same stance as the map's SX.
     */
    val contested: Boolean,
    val rivalNames: List<String>,
)

/** A responder activation that is currently in force, with the official who granted it. */
data class RoleGrantRecord(
    val subjectId: String,
    val subjectName: String,
    val atMs: Long,
    val byName: String,
)

/** Somebody who has asked to be activated and has not been answered. */
data class RoleApplication(
    val authorId: String,
    val authorName: String,
    val atMs: Long,
)

data class RoleState(
    val seats: List<SeatHolder> = emptyList(),
    val roleByAuthor: Map<String, String> = emptyMap(),
    val grants: List<RoleGrantRecord> = emptyList(),
    val pending: List<RoleApplication> = emptyList(),
) {
    fun roleOf(authorId: String): String = roleByAuthor[authorId] ?: LocalIdentity.ROLE_RESIDENT

    fun seatOf(authorId: String): SeatHolder? = seats.firstOrNull { it.authorId == authorId }

    fun grantOf(authorId: String): RoleGrantRecord? = grants.firstOrNull { it.subjectId == authorId }

    fun hasApplied(authorId: String): Boolean = pending.any { it.authorId == authorId }
}

/**
 * The whole role system, as a pure fold over the same append-only log everything else
 * reads. Roles used to be a string in SharedPreferences: private to one device,
 * invisible to the mesh, and settable by anyone who found the screen.
 *
 * **Order independence is the requirement, not a nicety.** NFR-4 says two devices
 * holding the same events must display the same state, and the mesh delivers in
 * whatever order the radio managed. So this never walks the log once accumulating as it
 * goes — a grant arriving before the claim that made its author an official would be
 * dropped on the floor. It resolves in three passes over the whole set instead, each
 * one a function of the pass before, with every ordering decision made by an explicit
 * sort on (timestamp, id) rather than by arrival.
 *
 * **What this is not.** Ground rule 4 forbids crypto, so nothing here is a signature
 * check. A device that forges a `role_grant` naming itself is indistinguishable from a
 * real one, and this fold will honour it. What the fold does buy is that every claim is
 * *attributable* (it carries a name and a time), *replicated* (every phone in the
 * barangay folds the same answer), and *contestable* (two devices claiming one seat
 * shows up as a conflict instead of silently disagreeing). That is a procedure, not a
 * guarantee, and `RoleScreen` says so on screen.
 */
fun foldRoles(events: List<Event>, roster: List<BarangaySeat>): RoleState {
    val seatsById = roster.associateBy { it.id }

    // Pass 1 — seats. Authority enters the system here and nowhere else.
    val claims = events
        .filter { it.type == TYPE_ROLE_CLAIM }
        .mapNotNull { event -> decodeRolePayload(event.payload)?.let { event to it } }
        .filter { (_, payload) -> payload.seatId != null && seatsById.containsKey(payload.seatId) }

    val seats = claims
        .groupBy { (_, payload) -> payload.seatId!! }
        .map { (seatId, forSeat) ->
            // Earliest claim wins; the event id breaks a same-millisecond tie so that
            // two devices with the same two events never disagree about the winner.
            val ordered = forSeat.sortedWith(compareBy({ it.first.timestampMs }, { it.first.id }))
            val (winnerEvent, winnerPayload) = ordered.first()
            val rivals = ordered.drop(1)
                .map { it.second.subjectName }
                .filter { it != winnerPayload.subjectName }
                .distinct()
            SeatHolder(
                seatId = seatId,
                seatTitle = seatsById.getValue(seatId).title,
                authorId = winnerPayload.subjectId,
                authorName = winnerPayload.subjectName,
                sinceMs = winnerEvent.timestampMs,
                contested = ordered.map { it.second.subjectId }.distinct().size > 1,
                rivalNames = rivals,
            )
        }
        .sortedBy { it.seatId }

    // A seat marked `canPostOfficialStatus = false` is an office without a say over
    // flood severity — held, but not official for this app's purposes.
    val officialIds = seats
        .filter { seatsById.getValue(it.seatId).canPostOfficialStatus }
        .map { it.authorId }
        .toSet()

    // Pass 2 — activations, but only those authored by somebody pass 1 made an official.
    val decisions = events
        .filter { it.type == TYPE_ROLE_GRANT || it.type == TYPE_ROLE_REVOKE }
        .filter { it.authorId in officialIds }
        .mapNotNull { event -> decodeRolePayload(event.payload)?.let { event to it } }
        // An official may activate a responder and nothing else. Officials come from
        // the roster, so authority cannot spread sideways from one holder to the next.
        .filter { (event, payload) ->
            event.type == TYPE_ROLE_REVOKE || payload.role == LocalIdentity.ROLE_RESPONDER
        }
        .sortedWith(compareBy({ it.first.timestampMs }, { it.first.id }))

    val latestDecision = LinkedHashMap<String, Pair<Event, RolePayload>>()
    for (decision in decisions) latestDecision[decision.second.subjectId] = decision

    val grants = latestDecision.values
        .filter { (event, _) -> event.type == TYPE_ROLE_GRANT }
        .map { (event, payload) ->
            RoleGrantRecord(
                subjectId = payload.subjectId,
                subjectName = payload.subjectName,
                atMs = event.timestampMs,
                byName = event.authorName,
            )
        }
        .sortedBy { it.subjectName }

    val roleByAuthor = buildMap {
        for (grant in grants) put(grant.subjectId, LocalIdentity.ROLE_RESPONDER)
        // Written last so a seat holder who was also granted responder still reads as
        // official — the higher authority, and the one the reducer weights at 5.
        for (id in officialIds) put(id, LocalIdentity.ROLE_OFFICIAL)
    }

    // Pass 3 — applications nobody has answered yet.
    val latestRequest = LinkedHashMap<String, Pair<Event, RolePayload>>()
    events
        .filter { it.type == TYPE_ROLE_REQUEST }
        .mapNotNull { event -> decodeRolePayload(event.payload)?.let { event to it } }
        .sortedWith(compareBy({ it.first.timestampMs }, { it.first.id }))
        .forEach { latestRequest[it.second.subjectId] = it }

    val pending = latestRequest.values
        .filter { (event, payload) ->
            val answer = latestDecision[payload.subjectId]?.first
            // Re-applying after a revoke is a fresh application; re-applying while
            // already activated is not.
            payload.subjectId !in officialIds &&
                roleByAuthor[payload.subjectId] != LocalIdentity.ROLE_RESPONDER &&
                (answer == null || event.timestampMs > answer.timestampMs)
        }
        .map { (event, payload) ->
            RoleApplication(
                authorId = payload.subjectId,
                authorName = payload.subjectName,
                atMs = event.timestampMs,
            )
        }
        .sortedByDescending { it.atMs }

    return RoleState(seats = seats, roleByAuthor = roleByAuthor, grants = grants, pending = pending)
}
