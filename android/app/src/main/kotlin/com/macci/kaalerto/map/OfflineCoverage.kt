package com.macci.kaalerto.map

import com.macci.kaalerto.demo.isInDemoArea
import kotlin.math.abs
import kotlin.math.cos

/**
 * Whether a point is inside the square [boundsAround] draws around a centre — the same
 * arithmetic, without MapLibre types, so coverage can be decided on the JVM.
 */
internal fun withinPack(
    lat: Double,
    lon: Double,
    centreLat: Double,
    centreLon: Double,
    halfExtentMeters: Double = HOME_HALF_EXTENT_M,
): Boolean {
    val latDelta = halfExtentMeters / 111_320.0
    val lonDelta = halfExtentMeters / (111_320.0 * cos(Math.toRadians(centreLat)).coerceAtLeast(0.01))
    return abs(lat - centreLat) <= latDelta && abs(lon - centreLon) <= lonDelta
}

/**
 * Whether this device can render [lat],[lon] with no network.
 *
 * The demo pack is always on disk. [homePackCentre] and [herePackCentre] are the centres
 * of the home and "here" packs, passed **only when that pack is [PackState.Ready]** — a
 * pack that is still downloading, failed, or was never created covers nothing, and
 * claiming otherwise is the false-progress the map's pack banner exists to avoid.
 */
fun isCovered(
    lat: Double,
    lon: Double,
    homePackCentre: Pair<Double, Double>?,
    herePackCentre: Pair<Double, Double>?,
): Boolean =
    isInDemoArea(lat, lon) ||
        (homePackCentre != null && withinPack(lat, lon, homePackCentre.first, homePackCentre.second)) ||
        (herePackCentre != null && withinPack(lat, lon, herePackCentre.first, herePackCentre.second))
