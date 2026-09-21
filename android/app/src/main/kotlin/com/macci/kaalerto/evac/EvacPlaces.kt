package com.macci.kaalerto.evac

import com.macci.kaalerto.location.Psgc

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

/**
 * Municipalities matching what is typed: the bundled PSGC list first (exact spellings, province included),
 * then any others already in use on a shelter or saved on this phone, for a place the list does not know.
 */
fun suggestMunicipalities(centres: List<EvacCentre>, own: String?, query: String, psgc: Psgc? = null): List<String> {
    val fromList = psgc?.search(query)?.map { it.label }.orEmpty()
    val seen = fromList.mapTo(HashSet()) { normalizePlace(it) }
    val inUse = suggest(query, knownSpellings(centres.map { it.municipality } + own)).filter { normalizePlace(it) !in seen }
    return (fromList + inUse).filter { it != query.trim() }.take(6)
}

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
    psgc: Psgc? = null,
): List<String> {
    if (normalizePlace(municipality).isEmpty()) return emptyList()
    // The chosen municipality's barangays from the bundled list, when it is one the list knows.
    val listed = psgc?.find(municipality)?.let { psgc.searchBarangays(it, query) }.orEmpty()
    val fromCentres = centres.filter { sameMunicipality(it.municipality, municipality) }.map { it.barangay }
    val own = if (sameMunicipality(ownMunicipality, municipality)) listOf(ownBarangay) else emptyList()
    val seen = listed.mapTo(HashSet()) { normalizePlace(it) }
    val inUse = suggest(query, knownSpellings(fromCentres + own)).filter { normalizePlace(it) !in seen }
    return (listed + inUse).filter { it != query.trim() }.take(8)
}
