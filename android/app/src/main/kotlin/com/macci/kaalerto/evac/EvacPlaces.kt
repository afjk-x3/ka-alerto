package com.macci.kaalerto.evac

/**
 * Municipality and barangay are free text, so two officials can spell the same place differently.
 * These helpers make the comparison forgiving (capitals and spacing) and suggest the spellings
 * already in use, so a person picks the existing one instead of inventing a new one.
 */

/** Lower-cased, trimmed, inner whitespace collapsed. */
fun normalizePlace(text: String?): String = text.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")

/** Two municipalities are the same when they normalise to the same non-empty text. */
fun sameMunicipality(a: String?, b: String?): Boolean {
    val na = normalizePlace(a)
    return na.isNotEmpty() && na == normalizePlace(b)
}

/**
 * One spelling per normalised place: the most common one, then the first seen. Blank entries are dropped.
 */
internal fun knownSpellings(places: List<String?>): List<String> =
    places.mapNotNull { it?.trim()?.ifBlank { null } }
        .groupBy { normalizePlace(it) }
        .values
        .map { spellings -> spellings.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key }

/** Up to [limit] known spellings containing [query] (ignoring case), starting-with first, never the exact text already typed. */
internal fun suggest(query: String, known: List<String>, limit: Int = 6): List<String> {
    val q = normalizePlace(query)
    return known
        .filter { it != query.trim() && normalizePlace(it).contains(q) }
        .sortedWith(compareBy({ !normalizePlace(it).startsWith(q) }, { normalizePlace(it) }))
        .take(limit)
}

/** Municipalities already in use: on any shelter, plus the one saved on this phone. */
fun suggestMunicipalities(centres: List<EvacCentre>, own: String?, query: String): List<String> =
    suggest(query, knownSpellings(centres.map { it.municipality } + own))

/**
 * Barangays already known for [municipality]: those on its shelters, plus this phone's own barangay
 * when its own municipality is that one. Nothing is suggested until a municipality is set.
 */
fun suggestBarangays(
    municipality: String?,
    centres: List<EvacCentre>,
    ownMunicipality: String?,
    ownBarangay: String?,
    query: String,
): List<String> {
    if (normalizePlace(municipality).isEmpty()) return emptyList()
    val fromCentres = centres.filter { sameMunicipality(it.municipality, municipality) }.map { it.barangay }
    val own = if (sameMunicipality(ownMunicipality, municipality)) listOf(ownBarangay) else emptyList()
    return suggest(query, knownSpellings(fromCentres + own))
}
