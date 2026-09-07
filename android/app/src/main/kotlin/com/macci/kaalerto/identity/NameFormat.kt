package com.macci.kaalerto.identity

/**
 * Turns what someone types into what the barangay sees.
 *
 * `Onboarding.dc.html` shows "Juan Dela Cruz" typed and "Juan D." previewed underneath,
 * and CLAUDE.md's decision table fixes that as the display form: first name plus an
 * initial, **never a full name or doorstep**.
 *
 * **The initial comes from the second word, not the last.** "Juan Dela Cruz" is
 * "Juan D." — Filipino surnames are routinely compound (Dela Cruz, De Guzman, San Jose,
 * Del Rosario), so taking the last token would render that person "Juan C.", which is
 * not their initial and is not what the artboard draws. First-token-plus-second-initial
 * gets the compound cases right and is identical to last-token for the simple ones
 * ("Maria Santos" → "Maria S." either way).
 */
fun displayFormOf(fullName: String): String {
    val words = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return ""
    val first = words.first()
    if (words.size == 1) return first
    val initial = words[1].first().uppercaseChar()
    return "$first $initial."
}

/**
 * The only check on a name, and deliberately the weakest one that still means something.
 *
 * Nothing here verifies anybody: the decision table is explicit that this is
 * *self-declared identification used for attribution*, never authentication. So this
 * refuses an empty field and nothing else. Rejecting names that "look fake" would be
 * both futile — the next person types "Juan" — and wrong, since a validator that decides
 * what counts as a real Filipino name will be wrong about somebody's actual name.
 */
fun isUsableName(fullName: String): Boolean = fullName.isNotBlank()
