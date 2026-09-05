package com.macci.kaalerto.map

import android.graphics.Color
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val GEOFENCE_SOURCE_ID = "kaalerto-geofence"
private const val GEOFENCE_FILL_LAYER_ID = "kaalerto-geofence-fill"
private const val GEOFENCE_LINE_LAYER_ID = "kaalerto-geofence-line"
private const val EARTH_RADIUS_M = 6_371_000.0
private const val CIRCLE_SIDES = 48

/**
 * `circle-radius` on a [org.maplibre.android.style.layers.CircleLayer] is always
 * screen pixels, not meters, so it can't represent a true geographic radius that
 * scales correctly with zoom — this generates an actual polygon instead, rendered as
 * a translucent fill + outline (the "drawn circle on map" for day 5's home-radius
 * picker).
 */
private fun circlePolygonPoints(lat: Double, lon: Double, radiusMeters: Double): List<Point> {
    val latRad = Math.toRadians(lat)
    return (0..CIRCLE_SIDES).map { i ->
        val angle = 2 * PI * i / CIRCLE_SIDES
        val dLat = (radiusMeters * cos(angle)) / EARTH_RADIUS_M
        val dLon = (radiusMeters * sin(angle)) / (EARTH_RADIUS_M * cos(latRad))
        Point.fromLngLat(lon + Math.toDegrees(dLon), lat + Math.toDegrees(dLat))
    }
}

/** Pass `center == null` to clear the circle (e.g. the draft was cancelled). */
fun updateGeofenceCircle(style: Style, center: Pair<Double, Double>?, radiusMeters: Double) {
    val collection = if (center == null) {
        FeatureCollection.fromFeatures(emptyArray())
    } else {
        val (lat, lon) = center
        val polygon = Polygon.fromLngLats(listOf(circlePolygonPoints(lat, lon, radiusMeters)))
        FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(polygon)))
    }

    val existingSource = style.getSourceAs<GeoJsonSource>(GEOFENCE_SOURCE_ID)
    if (existingSource != null) {
        existingSource.setGeoJson(collection)
        return
    }
    if (center == null) return // nothing to draw yet, and nothing was drawn before

    style.addSource(GeoJsonSource(GEOFENCE_SOURCE_ID, collection))
    val fillLayer = FillLayer(GEOFENCE_FILL_LAYER_ID, GEOFENCE_SOURCE_ID).withProperties(
        PropertyFactory.fillColor(Color.parseColor("#2F7FBF")),
        PropertyFactory.fillOpacity(0.15f),
    )
    val lineLayer = LineLayer(GEOFENCE_LINE_LAYER_ID, GEOFENCE_SOURCE_ID).withProperties(
        PropertyFactory.lineColor(Color.parseColor("#2F7FBF")),
        PropertyFactory.lineWidth(2f),
    )
    // Keep the geofence under the report markers so a marker near the edge stays
    // legible; addLayerBelow needs the target to already exist, which it will by the
    // time a user has gotten as far as long-pressing to set a home location.
    if (style.getLayer(EVENTS_LAYER_ID) != null) {
        style.addLayerBelow(fillLayer, EVENTS_LAYER_ID)
        style.addLayerBelow(lineLayer, EVENTS_LAYER_ID)
    } else {
        style.addLayer(fillLayer)
        style.addLayer(lineLayer)
    }
}
