package com.macci.kaalerto.identity

/**
 * Turns what someone types into what the barangay sees.
 *
 * `Onboarding.dc.html` shows "Juan Dela Cruz" typed and "Juan D." previewed underneath,
 * and CLAUDE.md's decision table fixes that as the display form: first name plus an
 * initial, **never a full name or doorstep**.
 *
 * **The given name and the surname are collected separately, and that is not a UI
 * preference.** With one field and the initial taken from the second word, "Juan Carlos
 * Santos" would render "Juan C." — an initial belonging to their own given name.
 * Compound given names (Juan Carlos, Maria Cristina, John Paul) and compound surnames
 * (Dela Cruz, De Guzman, San Jose) are both ordinary here, and no rule over a single
 * string can tell them apart. Two fields make it something the person states rather than
 * something the app guesses about their name.
 *
 * Ported from feat/event-sourced-roles.
 */
fun displayFormOf(firstName: String, lastName: String): String {
    val first = firstName.trim().replace(Regex("\\s+"), " ")
    val last = lastName.trim()
    if (first.isEmpty()) return last
    if (last.isEmpty()) return first
    return "$first ${last.first().uppercaseChar()}."
}

/**
 * The only checks on a name, and deliberately the weakest ones that still mean something.
 * This is self-declared identification used for attribution, never authentication, so
 * each part is refused only when empty and never checked against anything.
 */
fun isUsableName(firstName: String): Boolean = firstName.isNotBlank()

/**
 * **The surname is required** (it was optional until 14 Sep 2026). PRD §9's display form
 * is a first name *and* a last initial: a bare "Juan" is not enough to tell two neighbours
 * apart, which is the attribution the name exists for. Only its initial ever leaves the
 * device — see [displayFormOf].
 */
fun isUsableSurname(lastName: String): Boolean = lastName.isNotBlank()

/**
 * PRD §9 asks for a home barangay as well as a name. Same rule, same weakness: it is
 * never checked against a list, only refused when empty.
 */
fun isUsableBarangay(barangay: String): Boolean = barangay.isNotBlank()

/**
 * All three parts present — what the registration form requires, and what counts as
 * registered. A registration saved before the surname became required fails this, so
 * that device sees the form again, pre-filled, with only the surname left to add.
 */
fun isCompleteIdentity(firstName: String, lastName: String, barangay: String): Boolean =
    isUsableName(firstName) && isUsableSurname(lastName) && isUsableBarangay(barangay)
