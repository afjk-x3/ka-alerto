package com.macci.kaalerto.map

import android.content.Context

/**
 * Where the single "here" offline pack is centred, so coverage can be decided from prefs
 * alone — the same small-SharedPreferences shape as geofence/HomeLocationStore.kt.
 * Overwritten each time somebody downloads a new spot; there is only ever one.
 */
object HerePackStore {
    private const val PREFS = "kaalerto_here_pack"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"

    fun get(context: Context): Pair<Double, Double>? {
        val prefs = prefs(context)
        val lat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null
        return lat to lon
    }

    fun set(context: Context, lat: Double, lon: Double) {
        prefs(context).edit()
            .putString(KEY_LAT, lat.toString())
            .putString(KEY_LON, lon.toString())
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
