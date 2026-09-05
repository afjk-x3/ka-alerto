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
 */
object LocalIdentity {
    private const val PREFS_NAME = "kaalerto_identity"
    private const val KEY_AUTHOR_ID = "author_id"
    private const val KEY_AUTHOR_NAME = "author_name"
    private const val KEY_RESPONDER = "responder_mode"

    const val ROLE_RESIDENT = "resident"

    /**
     * BUILD_TASKS.md day 9's "responder mode toggle".
     *
     * In the real product a volunteer *applies* and the barangay *activates* them
     * (CLAUDE.md's decision table, and docs/03-architecture.md §1.6.10's supersession
     * note) — it is never self-granted. There is no barangay-side anything in this
     * build, so the toggle stands in for that activation, and the UI that flips it says
     * so in as many words rather than presenting it as a user setting.
     */
    const val ROLE_RESPONDER = "responder"

    data class Identity(val authorId: String, val authorName: String, val authorRole: String)

    fun getOrCreate(context: Context): Identity {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var authorId = prefs.getString(KEY_AUTHOR_ID, null)
        var authorName = prefs.getString(KEY_AUTHOR_NAME, null)

        if (authorId == null || authorName == null) {
            authorId = "local-${UUID.randomUUID()}"
            authorName = "Residente ${authorId.takeLast(4).uppercase()}"
            prefs.edit()
                .putString(KEY_AUTHOR_ID, authorId)
                .putString(KEY_AUTHOR_NAME, authorName)
                .apply()
        }

        val role = if (prefs.getBoolean(KEY_RESPONDER, false)) ROLE_RESPONDER else ROLE_RESIDENT
        return Identity(authorId, authorName, authorRole = role)
    }

    fun isResponder(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RESPONDER, false)

    /** Flipped only by the clearly-labelled demo control on the nearby-SOS screen. */
    fun setResponder(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RESPONDER, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
