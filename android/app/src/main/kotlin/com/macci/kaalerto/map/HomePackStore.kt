package com.macci.kaalerto.map

import android.content.Context

/**
 * Where the home offline pack (HOME_REGION_NAME) was actually built, which is not always
 * where the home is now. Same small-SharedPreferences shape as [HerePackStore].
 *
 * Coverage has to be decided against this centre, not the saved home. A home moved since
 * the pack was built (Profile, "Ituro sa mapa", or the map's long-press) would otherwise
 * read as covered offline while the region on disk still describes the old spot, and the
 * map would be blank there in airplane mode.
 *
 * Null means no home pack has been recorded: either none was ever built, or it was built
 * before this store existed.
 */
object HomePackStore {
    private const val PREFS = "kaalerto_home_pack"
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

/** What saving a home outside the demo area does to the home pack. */
enum class HomePackAction {
    /**
     * No built centre is recorded. Adopt the region already on disk (a pack from before
     * [HomePackStore] existed), or create one.
     */
    FIRST_BUILD,

    /** The home moved away from the centre the pack was built for. Replace the region. */
    REBUILD,

    /** Same home as the pack was built for. Resume or confirm it. */
    KEEP,
}

/**
 * Decides [HomePackAction] from the recorded built centre and the home being saved.
 * Any difference rebuilds: the pack should be centred on the saved home.
 */
fun homePackAction(builtCentre: Pair<Double, Double>?, newHome: Pair<Double, Double>): HomePackAction = when {
    builtCentre == null -> HomePackAction.FIRST_BUILD
    builtCentre != newHome -> HomePackAction.REBUILD
    else -> HomePackAction.KEEP
}

/**
 * Builds, rebuilds or resumes the home pack for a home saved at [lat],[lon], and records
 * the centre it is built for. Callers only call this for a home outside the demo area.
 *
 * [OfflineMapPack.replaceWith] matches regions by name, so a rebuild cannot touch the
 * demo or "here" pack.
 */
fun ensureHomePack(context: Context, lat: Double, lon: Double) {
    val bounds = boundsAround(lat, lon)
    val pack = OfflineMapPack(context, regionName = HOME_REGION_NAME, bounds = bounds)
    when (homePackAction(HomePackStore.get(context), lat to lon)) {
        HomePackAction.REBUILD -> pack.replaceWith(bounds)
        HomePackAction.FIRST_BUILD, HomePackAction.KEEP -> pack.ensureDownloaded()
    }
    HomePackStore.set(context, lat, lon)
}
