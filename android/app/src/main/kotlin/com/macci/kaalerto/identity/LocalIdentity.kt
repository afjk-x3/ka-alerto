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
 * **Roles are not self-granted.** A volunteer applies and the barangay activates them;
 * an official's authority comes from holding barangay office (CLAUDE.md's decision
 * table, `docs/03-architecture.md` §1.6.10's supersession note). That is now the actual
 * mechanism rather than a stand-in: roles are events, folded by [foldRoles] over the
 * same append-only log as everything else, and this object no longer has a setter a
 * screen can call. What is stored here is a *cache* of that fold — see
 * [cacheDerivedRole].
 */
object LocalIdentity {
    private const val PREFS_NAME = "kaalerto_identity"
    private const val KEY_AUTHOR_ID = "author_id"
    private const val KEY_AUTHOR_SUFFIX = "author_suffix"
    private const val KEY_ROLE = "role"

    /**
     * The name as typed, e.g. "Juan Dela Cruz". **Local to this device and nothing else.**
     *
     * The user's decision (7 Sep) was to keep the full name rather than only the derived
     * short form, so that the display rule can change later. That makes containment a
     * structural job rather than a matter of care: this key is read by exactly one
     * function ([registeredFullName], for pre-filling the edit field), [Identity] carries
     * only [displayFormOf] of it, and `IdentityPrivacyTest` asserts no event ever leaves
     * with the surname on it. Do not add a second reader without re-checking that test.
     */
    private const val KEY_FULL_NAME = "full_name"
    private const val KEY_FIRST_NAME = "first_name"
    private const val KEY_LAST_NAME = "last_name"
    private const val KEY_HOME_BARANGAY = "home_barangay"
    /**
     * Optional, unvalidated, and read by nothing today. Day 12's SMS fallback is the
     * eventual reader — it needs a number to send from and receive on — but that build
     * day does not exist yet, so this field cannot be required or format-checked
     * without gating a screen on a rule nothing enforces the reason for. PH mobile
     * numbers come in enough shapes (09XXXXXXXXX, +639XXXXXXXXX, spaced or not) that a
     * validator written now, against no real consumer, is more likely to reject a true
     * number than catch a false one.
     */
    private const val KEY_PHONE = "phone"

    const val ROLE_RESIDENT = "resident"
    const val ROLE_RESPONDER = "responder"

    /**
     * Barangay official. The reducer already gives this role Rule D's override and a
     * role weight of 5 (`data/Reducer.kt`), so this is the one role whose events change
     * what everyone else sees — which is why day 10's second-official gate exists.
     */
    const val ROLE_OFFICIAL = "official"

    val ALL_ROLES = listOf(ROLE_RESIDENT, ROLE_RESPONDER, ROLE_OFFICIAL)

    data class Identity(
        val authorId: String,
        val authorName: String,
        val authorRole: String,
        /**
         * The four characters kept across roles, so [displayName] can re-form the name
         * when a role changes. Defaulted from [authorId] the same way [getOrCreate]
         * derives it, so a hand-built Identity in a test does not have to restate it.
         */
        val suffix: String = authorId.takeLast(4).uppercase(),
    )

    /** Whether PRD §9's registration has been completed on this device. */
    fun isRegistered(context: Context): Boolean = registeredFirstName(context).isNotBlank()

    /**
     * For pre-filling the edit fields only — never for an event. See [KEY_FULL_NAME].
     *
     * Falls back to splitting the old single [KEY_FULL_NAME] on its first space, so a
     * device registered before the fields were separated keeps its name instead of being
     * asked again. Best effort by definition: the split is exactly the guess the two
     * fields exist to stop making, which is why it applies only to already-stored names
     * and never to anything typed from here on.
     */
    fun registeredFirstName(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_FIRST_NAME, null)?.takeIf { it.isNotBlank() }?.let { return it }
        return prefs.getString(KEY_FULL_NAME, null).orEmpty().trim().substringBefore(' ')
    }

    fun registeredLastName(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_LAST_NAME, null)?.let { return it }
        val legacy = prefs.getString(KEY_FULL_NAME, null).orEmpty().trim()
        return if (legacy.contains(' ')) legacy.substringAfter(' ').trim() else ""
    }

    fun homeBarangay(context: Context): String =
        prefs(context).getString(KEY_HOME_BARANGAY, null).orEmpty()

    fun registeredPhone(context: Context): String =
        prefs(context).getString(KEY_PHONE, null).orEmpty()

    /**
     * Registration, and re-registration when someone corrects a typo.
     *
     * `authorId` is deliberately untouched: it is what the mesh dedupes on, what the role
     * fold keys on, and what the false-alarm history counts against. Changing identity
     * here would orphan every one of those. Only the *name* moves, and only for events
     * authored from here on — the ones already on other phones keep what they were sent
     * with, because the log is append-only and the screen says so.
     */
    fun register(
        context: Context,
        firstName: String,
        lastName: String,
        phone: String,
        homeBarangay: String,
    ) {
        prefs(context).edit()
            .putString(KEY_FIRST_NAME, firstName.trim())
            .putString(KEY_LAST_NAME, lastName.trim())
            // The pre-split key is cleared rather than left behind, so nothing can read a
            // stale full name after the person has corrected how it splits.
            .remove(KEY_FULL_NAME)
            .putString(KEY_PHONE, phone.trim())
            .putString(KEY_HOME_BARANGAY, homeBarangay.trim())
            .apply()
    }

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
        val registered = displayFormOf(registeredFirstName(context), registeredLastName(context))
        return Identity(authorId, displayName(role, suffix, registered), role, suffix)
    }

    /**
     * The name embedded in events, which is what other devices render. It carries the
     * role's title because an official action must be attributable *as* an official act
     * — OfficialVerify.dc.html signs it "M. Reyes, Kagawad" and says "makikita ng lahat
     * kung sino ang nag-post". The four-character suffix is kept across roles so the
     * same person stays recognisably the same person on a receiving device.
     */
    fun displayName(role: String, suffix: String, registered: String = ""): String {
        // Before registration this is the day-3 stopgap: a generated four-character
        // placeholder that is deliberately not a guess at a real name.
        if (registered.isBlank()) return when (role) {
            ROLE_OFFICIAL -> "Kagawad $suffix"
            ROLE_RESPONDER -> "Responder $suffix"
            else -> "Residente $suffix"
        }
        // After it, the role title still rides along for the two roles where an action
        // has to be attributable *as* an official or responder act — OfficialVerify's
        // "M. Reyes, Kagawad". A resident is just their name.
        return when (role) {
            ROLE_OFFICIAL -> "Kagawad $registered"
            ROLE_RESPONDER -> "Responder $registered"
            else -> registered
        }
    }

    fun role(context: Context): String = prefs(context).getString(KEY_ROLE, null) ?: ROLE_RESIDENT

    /** Responder *or* official — both see the day 9 rescue queue. */
    fun isResponder(context: Context): Boolean = role(context) != ROLE_RESIDENT

    fun isOfficial(context: Context): Boolean = role(context) == ROLE_OFFICIAL

    /**
     * Write down what [foldRoles] most recently said this device's role is.
     *
     * This is a projection, not a source of truth — delete it and the next fold puts it
     * back. It exists because the guardrail in CLAUDE.md says the author's name and role
     * are embedded in an event *at creation*, so a receiving device renders them offline
     * with no lookup; and because the submitters that need it ([com.macci.kaalerto.report.submitReport],
     * `ConfirmDisputeSubmit`, `SosEvents`, `OfficialSubmit`, `EvacSubmit`) are on the
     * write path, where "nothing may await the network" applies just as much as on the
     * render path. Re-folding the whole log inside each of them to answer a question
     * already answered a frame ago would be the wrong trade.
     *
     * Only [RoleViewModel] calls this, and only with a value it just derived.
     */
    fun cacheDerivedRole(context: Context, role: String) {
        if (role(context) == role) return
        prefs(context).edit().putString(KEY_ROLE, role).apply()
    }

    /**
     * The superseded day-10 self-grant, alive only while [RoleMode.EVENT_SOURCED] is
     * `false` so that one device can walk every role by hand.
     *
     * Delete this together with [ManualRoleScreen] when the flag flips. It is exactly
     * the thing CLAUDE.md's decision table says roles must not be — a role you can give
     * yourself — and leaving it reachable in a shipped build would undo the rebuild
     * regardless of what the fold says.
     */
    fun setRoleForTesting(context: Context, role: String) {
        check(!RoleMode.EVENT_SOURCED) { "Roles are event-sourced; nothing may set one directly." }
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
