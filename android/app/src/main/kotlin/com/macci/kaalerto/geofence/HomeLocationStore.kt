package com.macci.kaalerto.geofence

import android.content.Context

data class HomeLocation(val lat: Double, val lon: Double, val radiusMeters: Double)

/**
 * A resident's saved home point + alert radius (BUILD_TASKS.md day 5). Local-only,
 * like everything else in this build — there is no server-side "home" concept.
 */
object HomeLocationStore {
    private const val PREFS = "kaalerto_home"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_RADIUS = "radius_m"

    const val DEFAULT_RADIUS_METERS = 300.0
    const val MIN_RADIUS_METERS = 100.0
    const val MAX_RADIUS_METERS = 1000.0

    fun get(context: Context): HomeLocation? {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_LAT)) return null
        return HomeLocation(
            lat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null,
            lon = prefs.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null,
            radiusMeters = prefs.getFloat(KEY_RADIUS, DEFAULT_RADIUS_METERS.toFloat()).toDouble(),
        )
    }

    fun set(context: Context, lat: Double, lon: Double, radiusMeters: Double) {
        prefs(context).edit()
            .putString(KEY_LAT, lat.toString())
            .putString(KEY_LON, lon.toString())
            .putFloat(KEY_RADIUS, radiusMeters.toFloat())
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
