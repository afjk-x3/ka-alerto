package com.macci.kaalerto.family

import com.macci.kaalerto.data.Event
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.identity.ROLE_TTL_MS
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/*
 * Mesh residual, disclosed rather than fixed — no crypto in this build (ground rule 4),
 * so there is nothing to encrypt it under, same trade-off `sos/SosMeshPolicy.kt` makes
 * for SOS.
 *
 * Both TYPE_CIRCLE_INVITE and TYPE_CHECKIN ride `mesh/MeshService.kt`'s relay in the
 * clear, type-agnostically — unlike the `sos*` family, nothing here strips or redacts
 * anything on the way out. Circle membership (`effectiveCircle`) and `circleStatuses`
 * filter *for display* on the receiving device; they decide what a phone chooses to
 * *show*, not what it stores or what crossed the air. Any phone in the barangay that
 * relays these events stores them in its own local database and can read straight out of
 * it: who invited whom (a `circle_invite` names both the inviter, in the event's own
 * `authorId`/`authorName`, and the target, in `CircleInvitePayload.targetAuthorId`), and
 * who checked in safe and when (a `family_checkin`'s own `authorId`/`authorName`/
 * `timestampMs`). None of that is limited to the two people actually in the circle.
 *
 * **The residual, stated plainly:** a pairing graph and a household's safety status are
 * both readable off any relaying device's local storage, not just the two circle
 * members' own phones. Nothing in this feature hides that from a peer who chooses to
 * look — only who a circle's members *choose to display it to* on their own screens is
 * controlled here.
 */

/** A "Ligtas ako" (I'm safe) presence ping. Carries no payload — the event's own
 * flat columns are enough (see [newCheckInEvent]). Rides the mesh in the clear — see the
 * file-level residual note above. */
const val TYPE_CHECKIN = "family_checkin"

/** A device that scanned another's QR posting "add this authorId back to your circle
 * too" — the mechanism that makes pairing mutual from one scan. See [newCircleInviteEvent].
 * Rides the mesh in the clear — see the file-level residual note above. */
const val TYPE_CIRCLE_INVITE = "circle_invite"

/**
 * A check-in is an observation that ages, the same way a flood report is — "last known
 * status: 3 days ago" is still shown by the reducer as long as the event exists (nothing
 * here filters on expiry), but 24h matches the order of magnitude every other
 * observation-type event in this app uses for how long the mesh actively keeps relaying
 * it, rather than inventing a new time constant with no precedent.
 */
const val CHECKIN_TTL_MS = 24L * 60 * 60 * 1000

private val circleJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * What rides in [Event.payload] for [TYPE_CIRCLE_INVITE]. The event's own `authorId`/
 * `authorName` already identify the inviter (standard rule, same as every other event
 * type) — this payload only needs to say who the invite is *for*.
 */
@Serializable
data class CircleInvitePayload(val targetAuthorId: String)

fun CircleInvitePayload.encode(): String = circleJson.encodeToString(CircleInvitePayload.serializer(), this)

fun decodeCircleInvitePayload(raw: String?): CircleInvitePayload? =
    raw?.let { runCatching { circleJson.decodeFromString(CircleInvitePayload.serializer(), it) }.getOrNull() }

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
 * Written by the **scanning** device immediately after it decodes the other party's QR.
 * `lat`/`lon` are the null-island sentinel — who is in a circle is not a fact about where
 * either phone was standing, same reasoning as `identity/RoleEvents.kt`'s role events.
 *
 * The TTL is [ROLE_TTL_MS] (a year), not [CHECKIN_TTL_MS], and that difference is
 * load-bearing: `family/CircleStore.kt`'s `effectiveCircle` folds *every* invite event
 * targeting a device on every read, forever — if this event purged on the same short
 * clock as a check-in, a pairing would silently come undone once the invite aged out of
 * local storage, with no record anywhere of why. Membership is not an observation that
 * goes stale, exactly the same call `identity/RoleEvents.kt` already made for roles.
 */
fun newCircleInviteEvent(
    identity: LocalIdentity.Identity,
    targetAuthorId: String,
    nowMs: Long,
): Event = Event(
    id = "circle-invite-${UUID.randomUUID()}",
    type = TYPE_CIRCLE_INVITE,
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
    payload = CircleInvitePayload(targetAuthorId = targetAuthorId).encode(),
)
