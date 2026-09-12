package com.macci.kaalerto.identity

import android.content.Context
import java.util.UUID

/**
 * This device's author identity: PRD §9's name + home barangay, collected once by
 * [OnboardingScreen], and the stable `authorId` every event carries.
 *
 * Registration is **identification used for attribution, never authentication** — nothing
 * is checked, and a name never raises a report's confidence (CLAUDE.md's decision table).
 * It exists so that filing a false report has a social cost.
 *
 * **Only the display form ever leaves the device.** The surname is stored so the display
 * rule can change later, but [getOrCreate] hands submitters [displayFormOf] of it — "Juan
 * D." — and `NameFormatTest` asserts the surname never reaches that string. The home
 * barangay is stored here too but is not embedded in events on this build; the event
 * schema has no field for it.
 *
 * Before registration, reports fall back to the day-3 stopgap: a generated placeholder
 * ("Residente 89A7") that deliberately does not look like a real person's name. This build
 * gates every authoring path on [isRegistered], so that fallback is a second line of
 * defence rather than something a resident normally sees.
 *
 * Ported, trimmed, from feat/event-sourced-roles: residents only (no roles), no phone
 * number, no home pin.
 */
object LocalIdentity {
    private const val PREFS_NAME = "kaalerto_identity"
    private const val KEY_AUTHOR_ID = "author_id"
    private const val KEY_FIRST_NAME = "first_name"
    private const val KEY_LAST_NAME = "last_name"
    private const val KEY_HOME_BARANGAY = "home_barangay"

    data class Identity(val authorId: String, val authorName: String, val authorRole: String)

    /** Whether PRD §9's registration has been completed on this device. */
    fun isRegistered(context: Context): Boolean = registeredFirstName(context).isNotBlank()

    /** For pre-filling the form only — never for an event. */
    fun registeredFirstName(context: Context): String = prefs(context).getString(KEY_FIRST_NAME, null).orEmpty()

    /** For pre-filling the form only — never for an event. */
    fun registeredLastName(context: Context): String = prefs(context).getString(KEY_LAST_NAME, null).orEmpty()

    fun homeBarangay(context: Context): String = prefs(context).getString(KEY_HOME_BARANGAY, null).orEmpty()

    /**
     * `authorId` is deliberately untouched: it is what dedup and the reducer's
     * one-live-position-per-author rule key on. Only the *name* moves, and only for events
     * authored from here on — ones already written keep what they were written with,
     * because the log is append-only.
     */
    fun register(context: Context, firstName: String, lastName: String, homeBarangay: String) {
        prefs(context).edit()
            .putString(KEY_FIRST_NAME, firstName.trim())
            .putString(KEY_LAST_NAME, lastName.trim())
            .putString(KEY_HOME_BARANGAY, homeBarangay.trim())
            .apply()
    }

    fun getOrCreate(context: Context): Identity {
        val prefs = prefs(context)
        var authorId = prefs.getString(KEY_AUTHOR_ID, null)
        if (authorId == null) {
            authorId = "local-${UUID.randomUUID()}"
            prefs.edit().putString(KEY_AUTHOR_ID, authorId).apply()
        }
        val registered = displayFormOf(registeredFirstName(context), registeredLastName(context))
        return Identity(authorId, displayName(authorId.takeLast(4).uppercase(), registered), authorRole = "resident")
    }

    /** The name embedded in events, which is what every other device renders. */
    fun displayName(suffix: String, registered: String): String =
        if (registered.isBlank()) "Residente $suffix" else registered

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
