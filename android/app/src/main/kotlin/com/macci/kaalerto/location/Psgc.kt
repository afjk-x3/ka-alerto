package com.macci.kaalerto.location

import android.content.Context
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The Philippine Standard Geographic Code list (PSA), bundled as `assets/psgc.json` by
 * `tools/psgc/build.py`: every city and municipality with its province, and the barangays of each.
 * Names and codes only, no coordinates, so it cannot say where a point is; it turns what a geocoder
 * or a person says ("Mapandan", "city of laoag") into the one exact spelling, and it works offline.
 *
 * The same file is bundled in the dashboard (`dashboard/src/data/psgc.json`).
 */
data class PsgcMunicipality(val code: String, val name: String, val province: String, val isCity: Boolean) {
    /** "Mapandan, Pangasinan" — what is stored and shown, so two "San Nicolas" stay apart. */
    val label: String get() = "$name, $province"
}

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val NOT_ALNUM = Regex("[^a-z0-9]+")

/** Lower-case, accents and punctuation gone, so "Peñablanca" and "penablanca" compare equal. */
internal fun foldName(text: String?): String =
    Normalizer.normalize(text.orEmpty(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase().replace(NOT_ALNUM, " ").trim()

/** "City of Laoag", "Laoag City", "Municipality of X", "X (Pob.)" -> the bare name. */
internal fun bareName(text: String?): String =
    foldName(text)
        .removePrefix("city of ").removePrefix("municipality of ").removePrefix("brgy ").removePrefix("barangay ")
        // Someone who has only typed "Brgy" has not named one yet: every barangay is still on offer.
        .let { if (it == "brgy" || it == "barangay") "" else it }
        .removeSuffix(" city").removeSuffix(" pob").trim()

class Psgc(val municipalities: List<PsgcMunicipality>, private val barangaysByCode: Map<String, List<String>>) {

    private val byLabel = municipalities.associateBy { foldName(it.label) }

    /** The entry whose label is exactly [label] (ignoring case, accents and punctuation), or null. */
    fun find(label: String?): PsgcMunicipality? = byLabel[foldName(label)]

    /**
     * Up to [limit] places matching what has been typed: starting with it first, then containing it. Matches
     * the name, or the "name, province" label, so "san nicolas ilocos" finds the right one.
     */
    fun search(query: String, limit: Int = 6): List<PsgcMunicipality> {
        val q = foldName(query)
        if (q.isEmpty()) return emptyList()
        return municipalities
            .filter { foldName(it.label).contains(q) }
            .sortedWith(compareBy({ !foldName(it.label).startsWith(q) }, { it.name }, { it.province }))
            .take(limit)
    }

    /** A barangay as this app writes it: "Brgy. San Juan Bautista" (PSGC's "(Pob.)" marker dropped). */
    private fun display(barangay: String): String = "Brgy. " + barangay.replace(Regex("\\s*\\(Pob\\.?\\)\\s*$"), "").trim()

    fun barangays(municipality: PsgcMunicipality): List<String> = barangaysByCode[municipality.code].orEmpty().map(::display)

    fun searchBarangays(municipality: PsgcMunicipality, query: String, limit: Int = 8): List<String> {
        val q = bareName(query)
        val all = barangays(municipality)
        if (q.isEmpty()) return all.take(limit)
        return all.filter { bareName(it).contains(q) }.sortedBy { !bareName(it).startsWith(q) }.take(limit)
    }

    /** The barangay of [municipality] that [name] refers to, in this app's spelling, or null. */
    fun matchBarangay(municipality: PsgcMunicipality, name: String?): String? {
        val wanted = bareName(name)
        if (wanted.isEmpty()) return null
        return barangays(municipality).firstOrNull { bareName(it) == wanted }
    }

    /**
     * Which municipality a geocoder result means. The candidates are the fields where a Philippine city or
     * municipality lands ([locality], [subAdminArea]); the others are used to tell same-named places apart
     * ("San Nicolas" exists in three provinces). If the answer is still ambiguous this returns null rather
     * than guess: the fields stay for the person to fill, which is better than a confident wrong province.
     */
    fun matchGeocoder(locality: String?, subAdminArea: String?, adminArea: String?): PsgcMunicipality? {
        val hints = listOf(locality, subAdminArea, adminArea).map { foldName(it) }.filter { it.isNotEmpty() }
        for (candidate in listOf(locality, subAdminArea)) {
            val name = bareName(candidate)
            if (name.isEmpty()) continue
            val named = municipalities.filter { bareName(it.name) == name }
            if (named.size == 1) return named[0]
            if (named.size > 1) {
                val byProvince = named.filter { m -> foldName(m.province) in hints }
                if (byProvince.size == 1) return byProvince[0]
            }
        }
        return null
    }

    companion object {
        /** Parses `assets/psgc.json`. Malformed input gives an empty list, so nothing depends on it being present. */
        fun parse(json: String): Psgc = runCatching {
            val root = Json.parseToJsonElement(json).jsonObject
            val places = root.getValue("m").jsonArray.map { row ->
                val r = row.jsonArray
                PsgcMunicipality(r[0].jsonPrimitive.content, r[1].jsonPrimitive.content, r[2].jsonPrimitive.content, r[3].jsonPrimitive.int == 1)
            }
            val barangays = (root.getValue("b") as JsonObject).mapValues { (_, names) -> (names as JsonArray).map { it.jsonPrimitive.content } }
            Psgc(places, barangays)
        }.getOrElse { Psgc(emptyList(), emptyMap()) }
    }
}

/** The bundled list, parsed from assets once per process. */
object BundledPsgc {
    @Volatile private var cached: Psgc? = null

    suspend fun get(context: Context): Psgc = cached ?: withContext(Dispatchers.IO) {
        val raw = runCatching { context.applicationContext.assets.open("psgc.json").bufferedReader().use { it.readText() } }.getOrNull()
        (raw?.let(Psgc::parse) ?: Psgc(emptyList(), emptyMap())).also { cached = it }
    }
}
