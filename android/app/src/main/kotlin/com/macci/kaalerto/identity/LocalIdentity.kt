package com.macci.kaalerto.identity

import android.content.Context
import java.util.UUID

/**
 * Stopgap author identity until the real registration screen (PRD §9: name + home
 * barangay, required at first run) is built — no `BUILD_TASKS.md` day currently
 * schedules that screen, so a report authored today needs *something* to embed as
 * `authorName`/`authorId` per the architecture guardrail that the name is baked into
 * the event at creation, not looked up later.
 *
 * The generated name is deliberately a placeholder, not a guess at a real one — it's
 * visible to every other device per CLAUDE.md's decision that report authorship is
 * public, so it must not look like a real person's name it never collected.
 *
 * **Roles are not self-granted in the real product.** A volunteer applies and the
 * barangay activates them; an official's authority comes from holding barangay office
 * (CLAUDE.md's decision table, `docs/03-architecture.md` §1.6.10's supersession note).
 * There is no barangay-side anything in this build, so [setRole] stands in for that
 * activation, and every screen that flips it says so in as many words.
 */
object LocalIdentity {
    private const val PREFS_NAME = "kaalerto_identity"
    private const val KEY_AUTHOR_ID = "author_id"
    private const val KEY_AUTHOR_SUFFIX = "author_suffix"
    private const val KEY_ROLE = "role"

    const val ROLE_RESIDENT = "resident"
    const val ROLE_RESPONDER = "responder"

    /**
     * Barangay official. The reducer already gives this role Rule D's override and a
     * role weight of 5 (`data/Reducer.kt`), so this is the one role whose events change
     * what everyone else sees — which is why day 10's second-official gate exists.
     */
    const val ROLE_OFFICIAL = "official"

    val ALL_ROLES = listOf(ROLE_RESIDENT, ROLE_RESPONDER, ROLE_OFFICIAL)

    data class Identity(val authorId: String, val authorName: String, val authorRole: String)

    fun getOrCreate(context: Context): Identity {
        val prefs = prefs(context)
        var authorId = prefs.getString(KEY_AUTHOR_ID, null)
        var suffix = prefs.getString(KEY_AUTHOR_SUFFIX, null)

        if (authorId == null || suffix == null) {
            authorId = "local-${UUID.randomUUID()}"
            suffix = authorId.takeLast(4).uppercase()
            prefs.edit()
                .putString(KEY_AUTHOR_ID, authorId)
                .putString(KEY_AUTHOR_SUFFIX, suffix)
                .apply()
        }

        val role = role(context)
        return Identity(authorId, displayName(role, suffix), role)
    }

    /**
     * The name embedded in events, which is what other devices render. It carries the
     * role's title because an official action must be attributable *as* an official act
     * — OfficialVerify.dc.html signs it "M. Reyes, Kagawad" and says "makikita ng lahat
     * kung sino ang nag-post". The four-character suffix is kept across roles so the
     * same person stays recognisably the same person on a receiving device.
     */
    fun displayName(role: String, suffix: String): String = when (role) {
        ROLE_OFFICIAL -> "Kagawad $suffix"
        ROLE_RESPONDER -> "Responder $suffix"
        else -> "Residente $suffix"
    }

    fun role(context: Context): String = prefs(context).getString(KEY_ROLE, null) ?: ROLE_RESIDENT

    /** Responder *or* official — both see the day 9 rescue queue. */
    fun isResponder(context: Context): Boolean = role(context) != ROLE_RESIDENT

    fun isOfficial(context: Context): Boolean = role(context) == ROLE_OFFICIAL

    fun setRole(context: Context, role: String) {
        prefs(context).edit().putString(KEY_ROLE, role).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/** The short badge shown in the map header and on the official screens. */
fun roleBadge(role: String): String = when (role) {
    LocalIdentity.ROLE_OFFICIAL -> "KAGAWAD"
    LocalIdentity.ROLE_RESPONDER -> "RESPONDER"
    else -> "RESIDENTE"
}

fun roleLabel(role: String): Pair<String, String> = when (role) {
    LocalIdentity.ROLE_OFFICIAL -> "Barangay official" to "Kagawad — nakakapag-post ng opisyal na status"
    LocalIdentity.ROLE_RESPONDER -> "Responder" to "Nakikita ang listahan ng humihingi ng tulong"
    else -> "Residente" to "Nag-uulat at nagkukumpirma ng baha"
}
