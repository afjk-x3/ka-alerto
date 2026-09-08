package com.macci.kaalerto.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
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

private const val HOME_MARKER_SOURCE_ID = "kaalerto-home-marker"
private const val HOME_MARKER_LAYER_ID = "kaalerto-home-marker-symbol"
private const val HOME_MARKER_ICON = "kaalerto-home-marker-icon"
private const val HOME_MARKER_ICON_PX = 72

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

/**
 * The saved home point itself — [updateGeofenceCircle] only ever drew the radius around
 * it, never the point, so a resident had no way to see where "home" actually was, only
 * the area around an invisible centre. Pass `center == null` to clear it (draft
 * cancelled, or no home saved yet).
 */
fun updateHomeMarker(style: Style, center: Pair<Double, Double>?) {
    if (style.getImage(HOME_MARKER_ICON) == null) {
        style.addImage(HOME_MARKER_ICON, renderHomeMarkerIcon())
    }

    val collection = if (center == null) {
        FeatureCollection.fromFeatures(emptyArray())
    } else {
        val (lat, lon) = center
        FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(Point.fromLngLat(lon, lat))))
    }

    val existingSource = style.getSourceAs<GeoJsonSource>(HOME_MARKER_SOURCE_ID)
    if (existingSource != null) {
        existingSource.setGeoJson(collection)
        return
    }
    if (center == null) return // nothing to draw yet, and nothing was drawn before

    style.addSource(GeoJsonSource(HOME_MARKER_SOURCE_ID, collection))
    style.addLayer(
        SymbolLayer(HOME_MARKER_LAYER_ID, HOME_MARKER_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(HOME_MARKER_ICON),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(0.7f),
        ),
    )
}

/** Same blue as the geofence circle's own outline, so the pin reads as "this circle's centre". */
private fun renderHomeMarkerIcon(): Bitmap {
    val size = HOME_MARKER_ICON_PX.toFloat()
    val bitmap = Bitmap.createBitmap(HOME_MARKER_ICON_PX, HOME_MARKER_ICON_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2F7FBF")
    }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = size * 0.09f
        color = Color.WHITE
    }
    val centre = size / 2f
    val radius = size * 0.32f
    canvas.drawCircle(centre, centre, radius, fill)
    canvas.drawCircle(centre, centre, radius, ring)

    // A small house glyph inside the dot, matching EvacMarkers.kt's roof-over-doorway shape.
    val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = size * 0.045f
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    val roof = android.graphics.Path().apply {
        moveTo(size * 0.36f, size * 0.52f)
        lineTo(size * 0.50f, size * 0.38f)
        lineTo(size * 0.64f, size * 0.52f)
    }
    canvas.drawPath(roof, glyph)
    canvas.drawLine(size * 0.41f, size * 0.52f, size * 0.41f, size * 0.65f, glyph)
    canvas.drawLine(size * 0.59f, size * 0.52f, size * 0.59f, size * 0.65f, glyph)
    canvas.drawLine(size * 0.41f, size * 0.65f, size * 0.59f, size * 0.65f, glyph)

    return bitmap
}
