package com.macci.kaalerto.location

import android.content.Context
import com.macci.kaalerto.data.haversineMeters
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A name the geocoder once gave for a coordinate, kept for when there is no signal. */
@Serializable
data class CachedPlace(val lat: Double, val lon: Double, val label: String, val savedAtMs: Long)

/** Oldest entries past this are dropped. A few KB of prefs. */
const val PLACE_CACHE_MAX_ENTRIES = 200

/** How far a cached name may be from the point it is offered for. */
const val PLACE_CACHE_RADIUS_M = 1_000.0

/** A new lookup this close to an old one replaces it rather than adding a near-duplicate. */
private const val PLACE_CACHE_REPLACE_WITHIN_M = 50.0

/** The cached name nearest [lat],[lon] within [maxMeters], or null. Pure. */
fun nearestCached(
    entries: List<CachedPlace>,
    lat: Double,
    lon: Double,
    maxMeters: Double = PLACE_CACHE_RADIUS_M,
): CachedPlace? =
    entries
        .map { it to haversineMeters(lat, lon, it.lat, it.lon) }
        .filter { (_, meters) -> meters <= maxMeters }
        .minByOrNull { (_, meters) -> meters }
        ?.first

/**
 * [entries] with [added] appended as the newest, any entry within 50 m of it removed,
 * and the oldest dropped past [max]. Pure.
 */
fun withCached(
    entries: List<CachedPlace>,
    added: CachedPlace,
    max: Int = PLACE_CACHE_MAX_ENTRIES,
): List<CachedPlace> =
    (entries.filterNot { haversineMeters(it.lat, it.lon, added.lat, added.lon) < PLACE_CACHE_REPLACE_WITHIN_M } + added)
        .takeLast(max)

/**
 * Every successful geocode, so a place this phone has named before still has a name
 * offline. Seeded for free by registration (the home label) and by "download here".
 * Local only — it never travels, and nothing but [describePlace] reads it.
 */
object PlaceCache {
    private const val PREFS = "kaalerto_place_cache"
    private const val KEY_ENTRIES = "entries"
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(CachedPlace.serializer())

    fun nearest(context: Context, lat: Double, lon: Double): CachedPlace? =
        nearestCached(read(context), lat, lon)

    @Synchronized
    fun put(context: Context, lat: Double, lon: Double, label: String) {
        val updated = withCached(read(context), CachedPlace(lat, lon, label, System.currentTimeMillis()))
        prefs(context).edit().putString(KEY_ENTRIES, json.encodeToString(serializer, updated)).apply()
    }

    private fun read(context: Context): List<CachedPlace> =
        prefs(context).getString(KEY_ENTRIES, null)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
