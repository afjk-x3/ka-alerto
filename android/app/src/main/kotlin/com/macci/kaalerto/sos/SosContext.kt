package com.macci.kaalerto.sos

import kotlinx.serialization.Serializable

/**
 * The optional detail a requester can add *after* the request is already going out —
 * FR-4.3, and the line the artboard puts at the top of the screen in bold: "Padala na
 * ang lokasyon mo. Opsyonal lang ito." Nothing here gates transmission; each answer is
 * an amendment event that follows the original.
 *
 * Every field is nullable because every field is genuinely optional. A request with
 * nothing but coordinates is a valid, dispatchable request.
 *
 * Copy is lifted verbatim from design/artboards/SOSContext.dc.html rather than
 * retranslated, so the rescue card, the status screen and the responder queue all read
 * the same words for the same fact.
 */
@Serializable
data class SosContext(
    /** One of [PEOPLE_OPTIONS]. A bucket, not a number — nobody counts heads in a flood. */
    val people: String? = null,
    /** Any of [COMPANION_OPTIONS] — who is with them, which changes how a rescue is staffed. */
    val companions: List<String> = emptyList(),
    /** Any of [MEDICAL_OPTIONS]. "Wala" is exclusive: selecting it clears the rest. */
    val medical: List<String> = emptyList(),
    /** One of [WATER_OPTIONS]. */
    val water: String? = null,
    /** One of [TREND_OPTIONS] — rising water is the difference between urgent and critical. */
    val trend: String? = null,
) {
    val isEmpty: Boolean
        get() = people == null && companions.isEmpty() && medical.isEmpty() && water == null && trend == null

    /**
     * Later answers overwrite earlier ones field by field, so an amendment that only
     * sets the water level does not erase the people count sent a minute ago.
     */
    fun mergedWith(later: SosContext): SosContext = SosContext(
        people = later.people ?: people,
        companions = later.companions.ifEmpty { companions },
        medical = later.medical.ifEmpty { medical },
        water = later.water ?: water,
        trend = later.trend ?: trend,
    )

    companion object {
        val PEOPLE_OPTIONS = listOf("1", "2–4", "5–8", "9+")
        val COMPANION_OPTIONS = listOf("Bata", "Matanda", "PWD")
        const val MEDICAL_NONE = "Wala"
        val MEDICAL_OPTIONS = listOf("Gamot sa puso", "Buntis", "Sugatan", MEDICAL_NONE)
        val WATER_OPTIONS = listOf("Tuhod", "Baywang", "Dibdib", "Lampas ulo")
        val TREND_OPTIONS = listOf("Tumataas", "Humuhupa")

        /** "Wala" and an actual medical need are contradictory, so selecting either clears the other. */
        fun toggleMedical(current: List<String>, tapped: String): List<String> = when {
            tapped == MEDICAL_NONE -> if (MEDICAL_NONE in current) emptyList() else listOf(MEDICAL_NONE)
            tapped in current -> current - tapped
            else -> current.filterNot { it == MEDICAL_NONE } + tapped
        }
    }
}

/** Whether anything here needs to reach a responder before the others — drives the card's red row. */
val SosContext.hasMedicalNeed: Boolean
    get() = medical.any { it != SosContext.MEDICAL_NONE }
