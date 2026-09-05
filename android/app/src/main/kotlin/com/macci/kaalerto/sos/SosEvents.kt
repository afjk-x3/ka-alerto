package com.macci.kaalerto.sos

import com.macci.kaalerto.data.Event
import com.macci.kaalerto.identity.LocalIdentity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** An SOS request. Its own `id` is the sosId every later amendment and transition points at. */
const val TYPE_SOS = "sos"

/** Optional detail added after the fact — never a new request, always an amendment to one. */
const val TYPE_SOS_AMEND = "sos_amend"

/** One lifecycle transition. See [SosState] for why these are events rather than a column. */
const val TYPE_SOS_STATE = "sos_state"

val SOS_TYPES = setOf(TYPE_SOS, TYPE_SOS_AMEND, TYPE_SOS_STATE)

/**
 * An SOS lives ~12 hours before the mesh stops relaying it. Far longer than any flood
 * report's TTL (6 h at S3) and deliberately so: `docs/03-architecture.md` §6.4 — "the
 * SOS does not expire just because nobody was listening yet". A rescuer's phone coming
 * into range hours later should still pick it up.
 */
const val SOS_TTL_MS = 12L * 60 * 60 * 1000

private val sosJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * What rides in [Event.payload] for all three SOS event types. One shape rather than
 * three because the fields are genuinely optional per type, and a sealed hierarchy here
 * would buy nothing but ceremony.
 *
 * [sosId] is what stitches a request, its amendments and its transitions back together
 * after they have travelled separately over the mesh. It is carried here rather than in
 * `Event.featureRef` because `featureRef` means "the geohash cell this observation is
 * about", and the map's reducer groups on it — an SOS is not a flooded road segment and
 * must not appear as a marker.
 */
@Serializable
data class SosPayload(
    val sosId: String,
    val context: SosContext? = null,
    val state: SosState? = null,
    /** Metres of horizontal uncertainty on the fix, shown on the rescue card as "±6 m". */
    val accuracyMeters: Float? = null,
)

fun SosPayload.encode(): String = sosJson.encodeToString(SosPayload.serializer(), this)

fun decodeSosPayload(raw: String?): SosPayload? =
    raw?.let { runCatching { sosJson.decodeFromString(SosPayload.serializer(), it) }.getOrNull() }

/**
 * The request itself, written the instant the hold completes — before GPS is refined,
 * before any channel is tried, and before the context screen is even shown. FR-2.2 and
 * `docs/03-architecture.md` §6.1's t+0.0 row.
 */
fun newSosEvent(
    identity: LocalIdentity.Identity,
    lat: Double,
    lon: Double,
    accuracyMeters: Float?,
    nowMs: Long,
): Event {
    val sosId = "sos-${UUID.randomUUID()}"
    return Event(
        id = sosId,
        type = TYPE_SOS,
        lat = lat,
        lon = lon,
        // Null on purpose: the map's reducer groups by featureRef, and a rescue request
        // is not an observation of a flooded segment. Giving it a geohash would put a
        // severity marker on someone's house.
        featureRef = null,
        severity = null,
        waterLevel = null,
        authorId = identity.authorId,
        authorName = identity.authorName,
        authorRole = identity.authorRole,
        timestampMs = nowMs,
        expiresAt = nowMs + SOS_TTL_MS,
        origin = "local",
        hopCount = 0,
        note = null,
        payload = SosPayload(sosId = sosId, accuracyMeters = accuracyMeters).encode(),
    )
}

fun sosAmendEvent(
    sosId: String,
    identity: LocalIdentity.Identity,
    lat: Double,
    lon: Double,
    context: SosContext,
    nowMs: Long,
): Event = sosFollowUp(
    sosId = sosId,
    type = TYPE_SOS_AMEND,
    identity = identity,
    lat = lat,
    lon = lon,
    nowMs = nowMs,
    payload = SosPayload(sosId = sosId, context = context),
)

fun sosStateEvent(
    sosId: String,
    identity: LocalIdentity.Identity,
    lat: Double,
    lon: Double,
    state: SosState,
    nowMs: Long,
): Event = sosFollowUp(
    sosId = sosId,
    type = TYPE_SOS_STATE,
    identity = identity,
    lat = lat,
    lon = lon,
    nowMs = nowMs,
    payload = SosPayload(sosId = sosId, state = state),
)

private fun sosFollowUp(
    sosId: String,
    type: String,
    identity: LocalIdentity.Identity,
    lat: Double,
    lon: Double,
    nowMs: Long,
    payload: SosPayload,
): Event = Event(
    // Distinct id per follow-up so the append-only store keeps every one of them, and
    // so re-delivery over the mesh still collapses on the content-hash primary key.
    id = "$type-${UUID.randomUUID()}",
    type = type,
    lat = lat,
    lon = lon,
    featureRef = null,
    severity = null,
    waterLevel = null,
    authorId = identity.authorId,
    authorName = identity.authorName,
    authorRole = identity.authorRole,
    timestampMs = nowMs,
    // Follow-ups share the original's lifetime rather than starting their own, so a
    // "safe" written 11 hours in doesn't outlive the request it closes.
    expiresAt = nowMs + SOS_TTL_MS,
    origin = "local",
    hopCount = 0,
    note = null,
    payload = payload.encode(),
)
