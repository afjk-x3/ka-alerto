package com.macci.kaalerto.map

import com.macci.kaalerto.demo.isInDemoArea
import com.macci.kaalerto.geofence.HomeLocation

/**
 * Where the main map opens, as (lat, lon), or null for the frozen demo area.
 *
 * A resident who lives outside the demo area opens on their own home: the home pack
 * (OfflineMapPack, HOME_REGION_NAME) is built around it at registration or on a Profile
 * save, so it normally renders offline. A home moved by the map's long-press has no pack
 * built for it; the map's coverage note (HomePackStore, isCovered) says so rather than
 * this function guessing.
 *
 * A home inside the demo area returns null on purpose — demo phones must open exactly
 * where they always have, or the scripted demo stops being repeatable.
 *
 * Plain doubles rather than a MapLibre LatLng so this stays testable on the JVM.
 */
fun homeStart(home: HomeLocation?): Pair<Double, Double>? =
    home?.takeUnless { isInDemoArea(it.lat, it.lon) }?.let { it.lat to it.lon }
