package com.macci.kaalerto.location

private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

/**
 * Standard geohash encoding. Used as the `featureRef` fallback for a freshly authored
 * report: with no road-network graph to snap to (BUILD_TASKS.md day 3 explicitly skips
 * that), a geohash cell is the same fallback docs/03-architecture.md's own schema names
 * for `feature_ref` ("OSM way/relation id, area id, or geohash-8") — it's what lets day
 * 4's reducer group reports of the same flooded spot together at all.
 *
 * Precision 8 is ~19m x 19m at the equator — tight enough that two people describing
 * the same stretch of road land in the same cell, without merging genuinely different
 * nearby streets.
 */
fun geohashEncode(lat: Double, lon: Double, precision: Int = 8): String {
    var minLat = -90.0
    var maxLat = 90.0
    var minLon = -180.0
    var maxLon = 180.0
    val result = StringBuilder()
    var isEvenBit = true
    var bit = 0
    var ch = 0

    while (result.length < precision) {
        if (isEvenBit) {
            val mid = (minLon + maxLon) / 2
            if (lon >= mid) {
                ch = ch or (1 shl (4 - bit))
                minLon = mid
            } else {
                maxLon = mid
            }
        } else {
            val mid = (minLat + maxLat) / 2
            if (lat >= mid) {
                ch = ch or (1 shl (4 - bit))
                minLat = mid
            } else {
                maxLat = mid
            }
        }
        isEvenBit = !isEvenBit
        if (bit < 4) {
            bit++
        } else {
            result.append(BASE32[ch])
            bit = 0
            ch = 0
        }
    }
    return result.toString()
}
