package com.macci.kaalerto.identity

import com.macci.kaalerto.data.Event
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** A device asserts it holds a roster seat. The only way the official role ever enters the system. */
const val TYPE_ROLE_CLAIM = "role_claim"

/** A resident applies to be activated as a responder. Carries no authority by itself. */
const val TYPE_ROLE_REQUEST = "role_request"

/** An official activates an applicant. Only ever grants responder — see [BarangaySeat]. */
const val TYPE_ROLE_GRANT = "role_grant"

/** An official stands an activation down. */
const val TYPE_ROLE_REVOKE = "role_revoke"

val ROLE_TYPES = setOf(TYPE_ROLE_CLAIM, TYPE_ROLE_REQUEST, TYPE_ROLE_GRANT, TYPE_ROLE_REVOKE)

/**
 * A year. Every other event in this app is an observation that goes stale — a flooded
 * road at 06:00 says nothing about 18:00, and `EventRepository.deleteExpired` is what
 * stops the store growing without bound. A role is not an observation: "the barangay
 * activated Boy as a responder" does not become less true overnight, and expiring it
 * would silently demote every responder in the barangay mid-flood, on whichever phone
 * happened to purge first. Revocation is an event, not a timeout.
 */
const val ROLE_TTL_MS = 365L * 24 * 60 * 60 * 1000

private val roleJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * What rides in [Event.payload] for all four role events.
 *
 * [subjectId] is who the event is *about*, which is not the author on a grant or a
 * revoke — that asymmetry is the whole point of the type. [subjectName] is embedded for
 * the same reason every other name in this app is: a receiving device must render
 * "Inaktibo si Boy R. ni Kagawad — Purok 3" with no lookup and no network.
 */
@Serializable
data class RolePayload(
    val subjectId: String,
    val subjectName: String,
    val role: String,
    /** Only on [TYPE_ROLE_CLAIM]: which roster seat is being claimed. */
    val seatId: String? = null,
)

fun RolePayload.encode(): String = roleJson.encodeToString(RolePayload.serializer(), this)

fun decodeRolePayload(raw: String?): RolePayload? =
    raw?.let { runCatching { roleJson.decodeFromString(RolePayload.serializer(), it) }.getOrNull() }

/**
 * Role events carry no location and no `featureRef`.
 *
 * `featureRef = null` for the same reason the `sos*` family sets it null: the map's
 * reducer groups observations by geohash cell, and an activation is not an observation
 * of a flooded segment. A non-null one would drop a severity marker on whoever was
 * activated. `lat`/`lon` are 0.0 rather than the author's position because who holds a
 * barangay seat is not a fact about where they were standing when they claimed it, and
 * shipping a coordinate nobody reads is a privacy leak with no user.
 */
private fun roleEvent(
    type: String,
    author: LocalIdentity.Identity,
    payload: RolePayload,
    nowMs: Long,
): Event = Event(
    id = "role-${UUID.randomUUID()}",
    type = type,
    lat = 0.0,
    lon = 0.0,
    featureRef = null,
    severity = null,
    waterLevel = null,
    authorId = author.authorId,
    authorName = author.authorName,
    authorRole = author.authorRole,
    timestampMs = nowMs,
    expiresAt = nowMs + ROLE_TTL_MS,
    origin = "local",
    hopCount = 0,
    note = null,
    payload = payload.encode(),
)

/**
 * A claim is authored under the *official* name and role, not the claimant's previous
 * ones. The alternative reads wrong in a way that matters: a seat list saying
 * "Kagawad — Purok 1 · Residente 89A7" invites the reader to wonder whether a resident
 * has somehow taken a kagawad's seat, when all that happened is that the display name
 * was captured a moment before the claim took effect. Every event this device authors
 * from here on says "Kagawad 89A7", so the claim that started it should too.
 *
 * Nothing is being asserted that the event does not already assert — the whole content
 * of a claim is "I hold this office" — and the fold ignores `authorRole` regardless,
 * reading the payload and the roster instead.
 */
fun roleClaimEvent(author: LocalIdentity.Identity, seat: BarangaySeat, nowMs: Long): Event {
    val asOfficial = author.copy(
        authorName = LocalIdentity.displayName(LocalIdentity.ROLE_OFFICIAL, author.suffix),
        authorRole = LocalIdentity.ROLE_OFFICIAL,
    )
    return roleEvent(
        type = TYPE_ROLE_CLAIM,
        author = asOfficial,
        payload = RolePayload(
            subjectId = asOfficial.authorId,
            subjectName = asOfficial.authorName,
            role = LocalIdentity.ROLE_OFFICIAL,
            seatId = seat.id,
        ),
        nowMs = nowMs,
    )
}

fun roleRequestEvent(author: LocalIdentity.Identity, nowMs: Long): Event =
    roleEvent(
        type = TYPE_ROLE_REQUEST,
        author = author,
        payload = RolePayload(
            subjectId = author.authorId,
            subjectName = author.authorName,
            role = LocalIdentity.ROLE_RESPONDER,
        ),
        nowMs = nowMs,
    )

fun roleGrantEvent(
    official: LocalIdentity.Identity,
    subjectId: String,
    subjectName: String,
    nowMs: Long,
): Event = roleEvent(
    type = TYPE_ROLE_GRANT,
    author = official,
    payload = RolePayload(
        subjectId = subjectId,
        subjectName = subjectName,
        role = LocalIdentity.ROLE_RESPONDER,
    ),
    nowMs = nowMs,
)

fun roleRevokeEvent(
    official: LocalIdentity.Identity,
    subjectId: String,
    subjectName: String,
    nowMs: Long,
): Event = roleEvent(
    type = TYPE_ROLE_REVOKE,
    author = official,
    payload = RolePayload(
        subjectId = subjectId,
        subjectName = subjectName,
        role = LocalIdentity.ROLE_RESIDENT,
    ),
    nowMs = nowMs,
)
