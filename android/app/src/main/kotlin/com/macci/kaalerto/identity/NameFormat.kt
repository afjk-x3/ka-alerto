package com.macci.kaalerto.identity

/**
 * Turns what someone types into what the barangay sees.
 *
 * `Onboarding.dc.html` shows "Juan Dela Cruz" typed and "Juan D." previewed underneath,
 * and CLAUDE.md's decision table fixes that as the display form: first name plus an
 * initial, **never a full name or doorstep**.
 *
 * **The given name and the surname are collected separately, and that is not a UI
 * preference.** This first shipped as one field with the initial taken from the second
 * word, which is correct for "Juan Dela Cruz" and wrong for "Juan Carlos Santos" — it
 * would render that person "Juan C.", an initial belonging to their own given name.
 * Compound given names (Juan Carlos, Maria Cristina, John Paul) and compound surnames
 * (Dela Cruz, De Guzman, San Jose) are both ordinary here, and no rule over a single
 * string can tell them apart. Two fields make it something the person states rather than
 * something the app guesses about their name.
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
 *
 * Nothing here verifies anybody: the decision table is explicit that this is
 * *self-declared identification used for attribution*, never authentication. So this
 * refuses an empty given name and nothing else — **the surname is optional**, because
 * plenty of people go by one name and a form that refuses them would be wrong about a
 * real person rather than catching a fake one.
 */
fun isUsableName(firstName: String): Boolean = firstName.isNotBlank()
