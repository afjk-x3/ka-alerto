package com.macci.kaalerto.map

import com.macci.kaalerto.demo.isInOfflineCoverage
import com.macci.kaalerto.geofence.HomeLocation

/**
 * Where the main map opens, as (lat, lon), or null for the curated area.
 *
 * A resident whose home has no real offline coverage (neither the curated area nor the
 * whole of Pangasinan — see [isInOfflineCoverage]) opens on their own home: the home pack
 * (OfflineMapPack, HOME_REGION_NAME) is built around it at registration or on a Profile
 * save, so it normally renders offline. A home moved by the map's long-press has no pack
 * built for it; the map's coverage note (HomePackStore, isCovered) says so rather than
 * this function guessing.
 *
 * A home already covered returns null on purpose — those phones open exactly where they
 * always have, or a scripted run of the demo script stops being repeatable.
 *
 * Plain doubles rather than a MapLibre LatLng so this stays testable on the JVM.
 */
fun homeStart(home: HomeLocation?): Pair<Double, Double>? =
    home?.takeUnless { isInOfflineCoverage(it.lat, it.lon) }?.let { it.lat to it.lon }
