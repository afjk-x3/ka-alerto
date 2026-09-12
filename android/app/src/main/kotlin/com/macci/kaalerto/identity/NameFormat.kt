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
 * The only check on a name, and deliberately the weakest one that still means something.
 * This is self-declared identification used for attribution, never authentication, so it
 * refuses an empty given name and nothing else — **the surname is optional**, because
 * plenty of people go by one name.
 */
fun isUsableName(firstName: String): Boolean = firstName.isNotBlank()

/**
 * PRD §9 asks for a home barangay as well as a name. Same rule, same weakness: it is
 * never checked against a list, only refused when empty.
 */
fun isUsableBarangay(barangay: String): Boolean = barangay.isNotBlank()
