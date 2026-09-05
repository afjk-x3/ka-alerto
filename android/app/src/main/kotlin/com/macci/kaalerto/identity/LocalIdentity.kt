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

        // No role switching exists yet (BUILD_TASKS.md day 10) — every locally-authored
        // report is a resident report until then.
        return Identity(authorId, authorName, authorRole = "resident")
    }
}
