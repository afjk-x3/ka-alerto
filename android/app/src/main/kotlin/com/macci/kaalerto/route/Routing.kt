package com.macci.kaalerto.route

import com.macci.kaalerto.data.FeatureSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// Route alternatives from OSRM's free public demo server, ranked by how many CURRENT flooded
// features lie along each route. OSRM knows roads, not floods: a road with no report simply
// looks clear, so this ranks by the reports we have and never certifies a route as safe.
// Port of dashboard/src/lib/routing.ts. Demo-only dependency: the public server is
// best-effort and only routes for cars — self-host OSRM (or change OSRM_URL) beyond a demo.
private const val OSRM_URL = "https://router.project-osrm.org/route/v1/driving"

/** A flooded feature within this many metres of the route line counts as "along" it. */
private const val NEAR_M = 75.0

data class LatLon(val lat: Double, val lon: Double)

data class FloodPoint(val lat: Double, val lon: Double, val severity: String)

data class RouteOption(
    /** (lon, lat) pairs, in travel order. */
    val coords: List<Pair<Double, Double>>,
    val distanceM: Double,
    val durationS: Double,
    val s3: Int,
    val s2: Int,
    val sx: Int,
    val safest: Boolean,
) {
    val floodCount get() = s3 + s2 + sx
}

/** What [fetchRoutes] can come back with; a failure is a value so the UI never sees an exception. */
sealed interface RoutesResult {
    data class Ok(val options: List<RouteOption>) : RoutesResult
    /** No connection, or the routing service was unreachable. */
    data object NoConnection : RoutesResult
    data object NoRoute : RoutesResult
    data class ServiceError(val code: Int) : RoutesResult
}

@Serializable
private data class OsrmResponse(val code: String = "", val routes: List<OsrmRoute> = emptyList())

@Serializable
private data class OsrmRoute(val distance: Double, val duration: Double, val geometry: OsrmGeometry)

@Serializable
private data class OsrmGeometry(val coordinates: List<List<Double>>)

/** A raw OSRM route before ranking. */
internal data class RawRoute(val coords: List<Pair<Double, Double>>, val distanceM: Double, val durationS: Double)

private val json = Json { ignoreUnknownKeys = true }

/** Null when the body is not a usable OSRM answer. */
internal fun parseOsrm(body: String): List<RawRoute>? {
    val parsed = runCatching { json.decodeFromString<OsrmResponse>(body) }.getOrNull() ?: return null
    if (parsed.code != "Ok") return null
    return parsed.routes.map { r ->
        RawRoute(r.geometry.coordinates.map { it[0] to it[1] }, r.distance, r.duration)
    }
}

/** Current flooded spots worth avoiding: S3 (impassable), S2 (impassable for cars), SX (conflicting). */
fun floodPoints(summaries: List<FeatureSummary>): List<FloodPoint> =
    summaries
        .filter { !it.isStale && it.severity in AVOID }
        .map { FloodPoint(it.lat, it.lon, it.severity) }

private val AVOID = setOf("S2", "S3", "SX")

/** Metres from p to segment a-b on a local flat approximation (fine at city scale). */
private fun distToSegmentM(p: LatLon, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val kx = 111_320.0 * cos(Math.toRadians(p.lat))
    val ky = 110_540.0
    val ax = (a.first - p.lon) * kx
    val ay = (a.second - p.lat) * ky
    val bx = (b.first - p.lon) * kx
    val by = (b.second - p.lat) * ky
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0.0) 0.0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / len2))
    return hypot(ax + t * dx, ay + t * dy)
}

private fun nearRoute(p: FloodPoint, coords: List<Pair<Double, Double>>): Boolean {
    val at = LatLon(p.lat, p.lon)
    for (i in 1 until coords.size) {
        if (distToSegmentM(at, coords[i - 1], coords[i]) <= NEAR_M) return true
    }
    return false
}

private fun score(r: RouteOption) = r.s3 * 3 + r.s2 * 2 + r.sx

/**
 * Counts the flooded spots each route passes within [NEAR_M] of and sorts fewest first, then
 * quickest. Spots at the destination are the reason for going, not an obstacle on the way.
 */
internal fun rankRoutes(raw: List<RawRoute>, floods: List<FloodPoint>, to: LatLon): List<RouteOption> {
    val obstacles = floods.filter {
        hypot(
            (it.lat - to.lat) * 110_540.0,
            (it.lon - to.lon) * 111_320.0 * cos(Math.toRadians(to.lat)),
        ) > NEAR_M
    }
    val options = raw.map { r ->
        val hit = obstacles.filter { nearRoute(it, r.coords) }
        RouteOption(
            coords = r.coords,
            distanceM = r.distanceM,
            durationS = r.durationS,
            s3 = hit.count { it.severity == "S3" },
            s2 = hit.count { it.severity == "S2" },
            sx = hit.count { it.severity == "SX" },
            safest = false,
        )
    }.sortedWith(compareBy({ score(it) }, { it.durationS }))
    return options.mapIndexed { i, o -> if (i == 0) o.copy(safest = true) else o }
}

suspend fun fetchRoutes(from: LatLon, to: LatLon, floods: List<FloodPoint>): RoutesResult = withContext(Dispatchers.IO) {
    val url = "$OSRM_URL/${from.lon},${from.lat};${to.lon},${to.lat}?alternatives=3&overview=full&geometries=geojson&steps=false"
    val connection = try {
        URL(url).openConnection() as HttpURLConnection
    } catch (e: IOException) {
        return@withContext RoutesResult.NoConnection
    }
    try {
        // The public server 403s Android's default "Dalvik/..." agent and asks clients to identify themselves.
        connection.setRequestProperty("User-Agent", "KaAlerto/1.0 (community flood map; hackathon demo)")
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        val status = connection.responseCode
        if (status !in 200..299) return@withContext RoutesResult.ServiceError(status)
        val raw = parseOsrm(connection.inputStream.bufferedReader().use { it.readText() })
        if (raw.isNullOrEmpty()) RoutesResult.NoRoute else RoutesResult.Ok(rankRoutes(raw, floods, to))
    } catch (e: IOException) {
        RoutesResult.NoConnection
    } finally {
        connection.disconnect()
    }
}

fun describeRoute(r: RouteOption): String {
    val km = "%.1f".format(r.distanceM / 1000)
    val min = max(1, (r.durationS / 60).toInt())
    return "$km km · $min min"
}
