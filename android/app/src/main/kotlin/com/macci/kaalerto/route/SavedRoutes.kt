package com.macci.kaalerto.route

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * PRD FR-3.2's second alert scope: a route the resident saved from the route panel. A new
 * flood report within [SAVED_ROUTE_ALERT_M] of it raises the same local notification a
 * report near home does (`geofence/GeofenceNotifier.kt`), with no server involved.
 *
 * Kept on this phone only, like the home location — a saved route is where someone travels,
 * so it never goes into an event.
 */
@Serializable
data class SavedRoute(
    val id: String,
    /** The destination as the route panel named it ("Near Mapandan Catholic School…"). */
    val name: String,
    /** (lon, lat) pairs, thinned to [MAX_POINTS]. */
    val coords: List<Pair<Double, Double>>,
)

/** A report this close to a saved route's line counts as on it. */
const val SAVED_ROUTE_ALERT_M = 50.0

/** Saving a fourth drops the oldest. */
const val MAX_SAVED_ROUTES = 3

private const val MAX_POINTS = 400
private const val PREFS = "saved_routes"
private const val KEY = "routes"
private val json = Json { ignoreUnknownKeys = true }

object SavedRoutes {
    fun all(context: Context): List<SavedRoute> = runCatching {
        prefs(context).getString(KEY, null)?.let { json.decodeFromString<List<SavedRoute>>(it) }
    }.getOrNull().orEmpty()

    fun save(context: Context, name: String, coords: List<Pair<Double, Double>>) {
        val route = SavedRoute(id = "route-${System.currentTimeMillis()}", name = name, coords = thin(coords))
        write(context, (all(context) + route).takeLast(MAX_SAVED_ROUTES))
    }

    fun remove(context: Context, id: String) = write(context, all(context).filterNot { it.id == id })

    private fun write(context: Context, routes: List<SavedRoute>) {
        prefs(context).edit().putString(KEY, json.encodeToString(routes)).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Every [n]th point plus the last, so a long route stays small in prefs; 50 m of slack absorbs the loss. */
internal fun thin(coords: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    if (coords.size <= MAX_POINTS) return coords
    val n = (coords.size + MAX_POINTS - 1) / MAX_POINTS
    return coords.filterIndexed { i, _ -> i % n == 0 } + coords.last()
}

/** The first saved route whose line passes within [SAVED_ROUTE_ALERT_M] of the point, or null. */
fun savedRouteNear(lat: Double, lon: Double, routes: List<SavedRoute>): SavedRoute? =
    routes.firstOrNull { distanceToLineM(LatLon(lat, lon), it.coords) <= SAVED_ROUTE_ALERT_M }
