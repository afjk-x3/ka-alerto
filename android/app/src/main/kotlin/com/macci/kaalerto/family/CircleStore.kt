package com.macci.kaalerto.family

import android.content.Context
import com.macci.kaalerto.data.Event
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One paired household-circle member, as scanned by this device. */
@Serializable
data class CircleMember(val authorId: String, val displayName: String, val pairedAtMs: Long)

/**
 * The local address book: people *this device* explicitly scanned. SharedPreferences,
 * one key, JSON-encoded list — the same small-local-state pattern as
 * `geofence/HomeLocationStore.kt` and `identity/LocalIdentity.kt`. No Room table: there
 * is no need for two devices to ever agree on who is in a circle (NFR-4 doesn't apply
 * here the way it does to roles — a circle is a personal watch-list, not a shared fact
 * the barangay must agree on), so a replicated membership protocol would be
 * over-engineering against a feature the build plan's own cut ladder puts third-from-last.
 */
object CircleStore {
    private const val PREFS = "kaalerto_circle"
    private const val KEY_MEMBERS = "members"
    private val json = Json { ignoreUnknownKeys = true }
    private val membersSerializer = ListSerializer(CircleMember.serializer())

    fun get(context: Context): List<CircleMember> {
        val raw = prefs(context).getString(KEY_MEMBERS, null) ?: return emptyList()
        return runCatching { json.decodeFromString(membersSerializer, raw) }.getOrDefault(emptyList())
    }

    /** Dedups on `authorId` — re-scanning an existing member is a no-op, not a duplicate entry. */
    fun add(context: Context, member: CircleMember) {
        val current = get(context)
        if (current.any { it.authorId == member.authorId }) return
        val updated = current + member
        prefs(context).edit().putString(KEY_MEMBERS, json.encodeToString(membersSerializer, updated)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * "Who's in my circle" as a pure derived view, not something written on receipt of an
 * invite — no background watcher, no write-on-arrival, no idempotency bookkeeping
 * needed. This reuses the same reducer pattern every other piece of derived state in
 * this app already relies on (`data/Reducer.kt`, `sos/SosReducer.kt`,
 * `identity/RoleReducer.kt`) instead of inventing a new stateful one.
 *
 * [locallyAdded] only ever holds people *I* scanned (the inviter side, written
 * immediately at scan time). People who scanned *me* show up purely through this fold
 * over [TYPE_CIRCLE_INVITE] events — recomputed the same way on every call, never cached.
 *
 * Known caveat, acceptable for now (see `specs/2026-09-09-family-checkin-design.md`):
 * because this folds *every* matching invite event on every call, a member removed
 * locally in some future release would immediately reappear, since the original invite
 * event never leaves the log. Not a problem today — there is no removal UI yet.
 */
fun effectiveCircle(
    locallyAdded: List<CircleMember>,
    allEvents: List<Event>,
    myAuthorId: String,
): List<CircleMember> {
    val invited = allEvents
        .asSequence()
        .filter { it.type == TYPE_CIRCLE_INVITE }
        .mapNotNull { event -> decodeCircleInvitePayload(event.payload)?.let { event to it } }
        .filter { (_, payload) -> payload.targetAuthorId == myAuthorId }
        .map { (event, _) -> CircleMember(authorId = event.authorId, displayName = event.authorName, pairedAtMs = event.timestampMs) }
    return (locallyAdded + invited).distinctBy { it.authorId }
}
