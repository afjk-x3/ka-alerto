package com.macci.kaalerto.sos

import com.macci.kaalerto.data.Event

/** Everything the SOS screens and the rescue card render, folded from one request's events. */
data class SosSnapshot(
    val sosId: String,
    val startedAtMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float?,
    val state: SosState,
    val context: SosContext,
    val authorName: String,
    /** True when this request was authored on this device rather than relayed in from a peer. */
    val isMine: Boolean,
) {
    val isActive: Boolean get() = !state.isClosed
}

/**
 * The same shape as day 4's [com.macci.kaalerto.data.Reducer]: a pure fold from the
 * append-only event log to a display state, with no mutable request row anywhere. Two
 * devices holding the same SOS events must show the same state (NFR-4), which is only
 * true if the state is *derived* rather than stored and updated.
 *
 * Ordering is by `timestampMs`, but correctness does not depend on it — [mergeSosState]
 * is monotonic, so transitions arriving out of order over the mesh land on the same
 * answer as transitions arriving in order.
 */
object SosReducer {

    fun snapshot(sosId: String, allEvents: List<Event>, localAuthorId: String?): SosSnapshot? {
        val events = allEvents
            .filter { it.type in SOS_TYPES }
            .mapNotNull { event -> decodeSosPayload(event.payload)?.let { event to it } }
            .filter { (_, payload) -> payload.sosId == sosId }
            .sortedBy { (event, _) -> event.timestampMs }

        val (request, requestPayload) = events.firstOrNull { (event, _) -> event.type == TYPE_SOS } ?: return null

        var state = SosState.QUEUED
        var context = requestPayload.context ?: SosContext()

        for ((event, payload) in events) {
            when (event.type) {
                TYPE_SOS_STATE -> payload.state?.let { state = mergeSosState(state, it) }
                TYPE_SOS_AMEND -> payload.context?.let { context = context.mergedWith(it) }
            }
        }

        return SosSnapshot(
            sosId = sosId,
            startedAtMs = request.timestampMs,
            lat = request.lat,
            lon = request.lon,
            accuracyMeters = requestPayload.accuracyMeters,
            state = state,
            context = context,
            authorName = request.authorName,
            isMine = localAuthorId != null && request.authorId == localAuthorId,
        )
    }

    /** Every request this device knows about, newest first. */
    fun all(allEvents: List<Event>, localAuthorId: String?): List<SosSnapshot> = allEvents
        .filter { it.type == TYPE_SOS }
        .mapNotNull { decodeSosPayload(it.payload)?.sosId }
        .distinct()
        .mapNotNull { snapshot(it, allEvents, localAuthorId) }
        .sortedByDescending { it.startedAtMs }

    /**
     * The request this device is currently making, if any.
     *
     * Deliberately restricted to this device's own: a relayed-in neighbour's SOS is
     * somebody else's emergency and belongs on the responder screens (day 9's
     * `SOSNearby`), not in the banner telling *you* that help is being called.
     */
    fun activeMine(allEvents: List<Event>, localAuthorId: String?): SosSnapshot? =
        all(allEvents, localAuthorId).firstOrNull { it.isMine && it.isActive }
}
