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
    /** Relayed in over the mesh rather than authored here — the queue's "dumating via mesh" count. */
    val arrivedByMesh: Boolean,
    /** How many devices this request crossed to get here. 0 when authored locally. */
    val hopCount: Int,
    /**
     * The responder who last advanced this request, from the `sos_state` event that did
     * it — the artboard's "Papunta na si Boy". Null while nobody has claimed it.
     *
     * This is the requester's *counterpart* name, and it travels the mesh unredacted on
     * purpose: sos/SosMeshPolicy.kt strips the person asking for help, not the person
     * volunteering to walk into floodwater for them.
     */
    val claimedByName: String?,
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
        var claimedByName: String? = null

        for ((event, payload) in events) {
            when (event.type) {
                TYPE_SOS_STATE -> payload.state?.let { incoming ->
                    val merged = mergeSosState(state, incoming)
                    // Only credit a transition that was actually adopted, and only one
                    // a responder made. The requester's own QUEUED -> BEACONING and
                    // "Ligtas na ako" must not put their name on the card as the
                    // person coming to help.
                    if (merged != state && merged.rank >= SosState.ACKNOWLEDGED.rank && !merged.isClosed) {
                        claimedByName = event.authorName
                    }
                    state = merged
                }
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
            arrivedByMesh = request.origin == "mesh",
            hopCount = request.hopCount,
            claimedByName = claimedByName,
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

    /**
     * Everyone else's open requests, newest first — the responder queue's contents.
     * Excludes this device's own, which belong on the requester's own status screen.
     */
    fun activeOthers(allEvents: List<Event>, localAuthorId: String?): List<SosSnapshot> =
        all(allEvents, localAuthorId).filter { !it.isMine && it.isActive }
}
