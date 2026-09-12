package com.macci.kaalerto.family

import com.macci.kaalerto.data.Event

/** One circle member as the Family screen renders them. */
data class CircleMember(val authorId: String, val displayName: String, val pairedAtMs: Long)

/**
 * "Who's in my circle" as the connected component of the [TYPE_CIRCLE_INVITE] event
 * graph containing [myAuthorId] — a pure fold over the shared event log, same shape as
 * `data/Reducer.kt`/`sos/SosReducer.kt`/`identity/RoleReducer.kt`, recomputed on every
 * call rather than cached or persisted anywhere.
 *
 * Every `circle_invite` event is treated as one *undirected* edge between its author and
 * its target — who scanned whom is provenance, not a constraint on the result. "My
 * circle" is everyone reachable from me by following those edges, not just people who
 * directly invited me or whom I directly invited: if A and B have already paired, and I
 * pair with either one of them, I end up with both, with no fan-out messaging and no
 * shared circle identifier for any device to keep in sync. See
 * `specs/2026-09-12-circle-unification-redesign.md` for why this replaced an earlier,
 * per-device `circleId` model that could not make that guarantee.
 *
 * A member reachable only transitively — someone I never scanned and who never scanned
 * me — has no event of their own to supply a display name from, which is exactly why
 * [CircleInvitePayload] carries `targetAuthorName`: whichever edge first connects them to
 * the graph is also the only place their name is guaranteed to appear.
 */
fun effectiveCircle(allEvents: List<Event>, myAuthorId: String): List<CircleMember> {
    data class Edge(
        val authorId: String,
        val authorName: String,
        val targetId: String,
        val targetName: String,
        val atMs: Long,
    )

    val edges = allEvents
        .asSequence()
        .filter { it.type == TYPE_CIRCLE_INVITE }
        .mapNotNull { event ->
            decodeCircleInvitePayload(event.payload)?.let { payload ->
                Edge(event.authorId, event.authorName, payload.targetAuthorId, payload.targetAuthorName, event.timestampMs)
            }
        }
        .toList()

    val neighbors = mutableMapOf<String, MutableSet<String>>()
    val nameOf = mutableMapOf<String, String>()
    val firstSeenAt = mutableMapOf<String, Long>()
    for (edge in edges) {
        neighbors.getOrPut(edge.authorId) { mutableSetOf() }.add(edge.targetId)
        neighbors.getOrPut(edge.targetId) { mutableSetOf() }.add(edge.authorId)
        nameOf[edge.authorId] = edge.authorName
        nameOf[edge.targetId] = edge.targetName
        firstSeenAt.merge(edge.authorId, edge.atMs, ::minOf)
        firstSeenAt.merge(edge.targetId, edge.atMs, ::minOf)
    }

    // BFS from myAuthorId over the undirected invite graph.
    val visited = mutableSetOf(myAuthorId)
    val queue = ArrayDeque(listOf(myAuthorId))
    while (queue.isNotEmpty()) {
        for (neighbor in neighbors[queue.removeFirst()].orEmpty()) {
            if (visited.add(neighbor)) queue.add(neighbor)
        }
    }

    return visited
        .filterNot { it == myAuthorId }
        .map { id -> CircleMember(authorId = id, displayName = nameOf[id] ?: id, pairedAtMs = firstSeenAt[id] ?: 0L) }
}
