package com.macci.kaalerto.location

import android.content.Context
import com.macci.kaalerto.data.haversineMeters
import com.macci.kaalerto.demo.DemoArea
import com.macci.kaalerto.demo.isInDemoArea
import kotlin.math.cos
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A place label: the specific part first ("Sotto Street"), the wider area after it. */
data class PlaceName(val primary: String, val secondary: String?) {
    val oneLine: String get() = listOfNotNull(primary, secondary).joinToString(", ")
}

/** A named street centreline, as (lat, lon) points. */
data class NamedLine(val name: String, val points: List<Pair<Double, Double>>)

data class NamedPoint(val name: String, val lat: Double, val lon: Double)

/** A GPS fix indoors is often 20–40 m off; further than this from a street is not "on" it. */
private const val STREET_SNAP_METERS = 50.0

/** Close enough to a named building to give directions by it. */
private const val LANDMARK_NEAR_METERS = 200.0

/**
 * Names a point from the data the app already carries — the OSM street centrelines in
 * `assets/routes/` and the named facilities in `assets/evacuation_centres.json` — so a
 * place name works in airplane mode, the same as the map. It only knows the demo area:
 * outside [DemoArea] it returns null rather than guess.
 *
 * The barangay in the label is [DemoArea]'s own bounding box, which is not a surveyed
 * boundary (see DemoArea's class doc) — the same claim the map header already makes.
 */
class Gazetteer(private val streets: List<NamedLine>, private val landmarks: List<NamedPoint>) {

    fun describe(lat: Double, lon: Double): PlaceName? {
        if (!isInDemoArea(lat, lon)) return null
        val area = "${DemoArea.BARANGAY_NAME}, ${DemoArea.MUNICIPALITY}"

        streets
            .map { it.name to distanceToLineMeters(lat, lon, it.points) }
            .filter { (_, meters) -> meters <= STREET_SNAP_METERS }
            .minByOrNull { (_, meters) -> meters }
            ?.let { (name, _) -> return PlaceName(name, area) }

        landmarks
            .map { it.name to haversineMeters(lat, lon, it.lat, it.lon) }
            .filter { (_, meters) -> meters <= LANDMARK_NEAR_METERS }
            .minByOrNull { (_, meters) -> meters }
            ?.let { (name, _) -> return PlaceName("Malapit sa $name", area) }

        return PlaceName(DemoArea.BARANGAY_NAME, DemoArea.MUNICIPALITY)
    }
}

/**
 * Shortest distance from a point to a polyline, in metres. A local flat projection around
 * the point is plenty at street scale (errors well under a metre across a few hundred).
 */
internal fun distanceToLineMeters(lat: Double, lon: Double, points: List<Pair<Double, Double>>): Double {
    if (points.isEmpty()) return Double.MAX_VALUE
    val metersPerDegLat = 110_574.0
    val metersPerDegLon = 111_320.0 * cos(Math.toRadians(lat))
    val xy = points.map { (pLat, pLon) -> (pLon - lon) * metersPerDegLon to (pLat - lat) * metersPerDegLat }
    if (xy.size == 1) return xy[0].let { (x, y) -> sqrt(x * x + y * y) }
    return xy.zipWithNext().minOf { (a, b) -> distanceFromOriginToSegment(a.first, a.second, b.first, b.second) }
}

private fun distanceFromOriginToSegment(ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
    val px = ax + t * dx
    val py = ay + t * dy
    return sqrt(px * px + py * py)
}

/** Every named LineString in a GeoJSON FeatureCollection. Malformed input gives an empty list. */
internal fun parseStreets(geojson: String): List<NamedLine> = runCatching {
    Json.parseToJsonElement(geojson).jsonObject.getValue("features").jsonArray.mapNotNull { element ->
        val feature = element.jsonObject
        val name = feature["properties"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val geometry = feature["geometry"]?.jsonObject ?: return@mapNotNull null
        if (geometry["type"]?.jsonPrimitive?.contentOrNull != "LineString") return@mapNotNull null
        val points = geometry.getValue("coordinates").jsonArray.map { coordinate ->
            val lonLat = coordinate.jsonArray
            lonLat[1].jsonPrimitive.double to lonLat[0].jsonPrimitive.double
        }
        NamedLine(name, points)
    }
}.getOrDefault(emptyList())

/** The named centres in `evacuation_centres.json`. Malformed input gives an empty list. */
internal fun parseLandmarks(json: String): List<NamedPoint> = runCatching {
    Json.parseToJsonElement(json).jsonObject.getValue("centres").jsonArray.map { element ->
        val centre = element.jsonObject
        NamedPoint(
            name = centre.getValue("name").jsonPrimitive.content,
            lat = centre.getValue("lat").jsonPrimitive.double,
            lon = centre.getValue("lon").jsonPrimitive.double,
        )
    }
}.getOrDefault(emptyList())

/** The bundled [Gazetteer], parsed from assets once per process. */
internal object BundledPlaces {
    @Volatile private var cached: Gazetteer? = null

    suspend fun get(context: Context): Gazetteer = cached ?: withContext(Dispatchers.IO) {
        val assets = context.applicationContext.assets
        fun read(path: String): String? = runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
        val streets = assets.list("routes").orEmpty()
            .filter { it.endsWith(".geojson") }
            .flatMap { file -> read("routes/$file")?.let(::parseStreets).orEmpty() }
        val landmarks = read("evacuation_centres.json")?.let(::parseLandmarks).orEmpty()
        Gazetteer(streets, landmarks).also { cached = it }
    }
}

