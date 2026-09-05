package com.macci.kaalerto.ui.theme

/**
 * Canonical severity colors — constant across Normal/Storm/Survival modes, because a
 * colour meaning "impassable" cannot mean something else in a different theme
 * (docs/02-prd.md §6). Matches design/artboards/Report-Normal.dc.html's own severity
 * data exactly, which is the single source of truth for these hex values.
 */
object SeverityColors {
    const val S0 = "#2F7FBF"
    const val S1 = "#F2A93B"
    const val S2 = "#E4682B"
    const val S3 = "#C42B2B"
    /** Rule C conflict — deliberately not on the S0-S3 ladder (docs/03-architecture.md §4.4). */
    const val SX = "#6A1B9A"
    const val UNKNOWN = "#9E9E9E"

    fun forSeverity(severity: String?): String = when (severity) {
        "S0" -> S0
        "S1" -> S1
        "S2" -> S2
        "S3" -> S3
        "SX" -> SX
        else -> UNKNOWN
    }
}
