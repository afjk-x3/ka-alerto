package com.macci.kaalerto.family

import com.macci.kaalerto.data.Event
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.identity.ROLE_TTL_MS
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/*
 * Mesh AND Supabase residual, disclosed rather than fixed — no crypto in this build
 * (ground rule 4), so there is nothing to encrypt it under, same trade-off
 * `sos/SosMeshPolicy.kt` makes for SOS.
 *
 * TYPE_CHECKIN stays mesh-only (never added to sync/SupabaseSync.kt's SYNCED_TYPES).
 * TYPE_CIRCLE_CREATE and TYPE_CIRCLE_JOIN, unlike the pairwise circle_invite they
 * replaced, ARE on that allowlist (specs/2026-09-23-circle-create-join-redesign.md) —
 * a real household's membership and its chosen name now sit in Supabase's
 * access-control-free table permanently, not just readable off a relaying phone's
 * local database while mesh-only. This is a genuine step up in exposure from the
 * old pairwise-edge design, accepted in exchange for a join code that works from
 * anywhere rather than only within Bluetooth range — stated plainly, not glossed over.
 *
 * Circle membership (`family/CircleStore.kt`'s `resolveCircle`) is a fold over every
 * circle_create/circle_join event a device has seen. Any device holding those events
 * — a mesh relay, or anyone who extracts the embedded Supabase anon key — can read
 * the full membership and its chosen name straight out of them.
 */

/** A "Ligtas ako" (I'm safe) presence ping. Carries no payload — the event's own
 * flat columns are enough (see [newCheckInEvent]). Rides the mesh in the clear — see
 * the file-level residual note above. Not on Supabase's sync allowlist. */
const val TYPE_CHECKIN = "family_checkin"

/** Written once, by whoever taps "Gumawa ng Circle" — the entity's own creation
 * event, same role `evac_centre` plays for a shelter. See [newCircleCreateEvent]. */
const val TYPE_CIRCLE_CREATE = "circle_create"

/** Written by every device that later joins, by code or QR. See [newCircleJoinEvent]. */
const val TYPE_CIRCLE_JOIN = "circle_join"

/**
 * A check-in is an observation that ages, the same way a flood report is — "last known
 * status: 3 days ago" is still shown by the reducer as long as the event exists (nothing
 * here filters on expiry), but 24h matches the order of magnitude every other
 * observation-type event in this app uses for how long the mesh actively keeps relaying
 * it, rather than inventing a new time constant with no precedent.
 */
const val CHECKIN_TTL_MS = 24L * 60 * 60 * 1000

private val circleJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** What rides in [Event.payload] for [TYPE_CIRCLE_CREATE]. [circleId] is the only
 * shared identity every later `circle_join` needs to agree on — it originates from
 * exactly this one event, so there is no id-agreement protocol to get wrong. */
@Serializable
data class CircleCreatePayload(val circleId: String, val name: String)

fun CircleCreatePayload.encode(): String = circleJson.encodeToString(CircleCreatePayload.serializer(), this)

fun decodeCircleCreatePayload(raw: String?): CircleCreatePayload? =
    raw?.let { runCatching { circleJson.decodeFromString(CircleCreatePayload.serializer(), it) }.getOrNull() }

/** What rides in [Event.payload] for [TYPE_CIRCLE_JOIN]. No name field — the event's
 * own `authorName` column already says who joined, the same standard rule every other
 * event type in this app follows. */
@Serializable
data class CircleJoinPayload(val circleId: String)

fun CircleJoinPayload.encode(): String = circleJson.encodeToString(CircleJoinPayload.serializer(), this)

fun decodeCircleJoinPayload(raw: String?): CircleJoinPayload? =
    raw?.let { runCatching { circleJson.decodeFromString(CircleJoinPayload.serializer(), it) }.getOrNull() }

/**
 * The check-in itself. `featureRef = null`, same reasoning as the `sos*` family: a
 * presence ping is not an observation of a flooded segment, and `data/Reducer.kt` must
 * never see it. `lat`/`lon` are the null-island sentinel when the resident declines to
 * share position ("location optional" per `docs/03-architecture.md` §1.6.4) — see
 * `identity/RoleEvents.kt`'s identical convention for "no real coordinate here".
 */
fun newCheckInEvent(
    identity: LocalIdentity.Identity,
    lat: Double?,
    lon: Double?,
    nowMs: Long,
): Event = Event(
    id = "checkin-${UUID.randomUUID()}",
    type = TYPE_CHECKIN,
    lat = lat ?: 0.0,
    lon = lon ?: 0.0,
    featureRef = null,
    severity = null,
    waterLevel = null,
    authorId = identity.authorId,
    authorName = identity.authorName,
    authorRole = identity.authorRole,
    timestampMs = nowMs,
    expiresAt = nowMs + CHECKIN_TTL_MS,
    origin = "local",
    hopCount = 0,
    note = null,
    payload = null,
)

/**
 * Written once by the creator. The TTL is [ROLE_TTL_MS] (a year), not [CHECKIN_TTL_MS]
 * — membership is not an observation that goes stale, the same call
 * `identity/RoleEvents.kt` already made for roles.
 */
fun newCircleCreateEvent(
    identity: LocalIdentity.Identity,
    circleId: String,
    name: String,
    nowMs: Long,
): Event = Event(
    id = "circle-create-${UUID.randomUUID()}",
    type = TYPE_CIRCLE_CREATE,
    lat = 0.0,
    lon = 0.0,
    featureRef = null,
    severity = null,
    waterLevel = null,
    authorId = identity.authorId,
    authorName = identity.authorName,
    authorRole = identity.authorRole,
    timestampMs = nowMs,
    expiresAt = nowMs + ROLE_TTL_MS,
    origin = "local",
    hopCount = 0,
    note = null,
    payload = CircleCreatePayload(circleId = circleId, name = name).encode(),
)

/** Written by a device joining an existing circle, whether the code arrived by text
 * or QR. Same TTL reasoning as [newCircleCreateEvent]. */
fun newCircleJoinEvent(
    identity: LocalIdentity.Identity,
    circleId: String,
    nowMs: Long,
): Event = Event(
    id = "circle-join-${UUID.randomUUID()}",
    type = TYPE_CIRCLE_JOIN,
    lat = 0.0,
    lon = 0.0,
    featureRef = null,
    severity = null,
    waterLevel = null,
    authorId = identity.authorId,
    authorName = identity.authorName,
    authorRole = identity.authorRole,
    timestampMs = nowMs,
    expiresAt = nowMs + ROLE_TTL_MS,
    origin = "local",
    hopCount = 0,
    note = null,
    payload = CircleJoinPayload(circleId = circleId).encode(),
)
