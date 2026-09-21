package com.macci.kaalerto.map

import android.graphics.Color
import com.macci.kaalerto.route.LatLon
import com.macci.kaalerto.route.RouteOption
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// `kaalerto-` prefix: Storm Mode's re-tint skips our own layers (map/StormMapStyle.kt).
private const val ROUTES_SOURCE_ID = "kaalerto-routes"
private const val ROUTES_CASING_ID = "kaalerto-routes-casing"
private const val ROUTES_LINE_ID = "kaalerto-routes-line"
private const val ORIGIN_SOURCE_ID = "kaalerto-route-origin"
private const val ORIGIN_LAYER_ID = "kaalerto-route-origin-dot"

/**
 * Draws the route alternatives, the highlighted one on top, and "you are here". Idempotent:
 * call with an empty list to clear. Sources and layers are created on first use and only
 * have their data swapped afterwards, so a style reload (Storm toggle) followed by another
 * call rebuilds them.
 */
fun updateRouteLayers(style: Style, routes: List<RouteOption>, active: Int, origin: LatLon?) {
    // Data-driven styling reads plain numbers: 1 = highlighted / safest.
    val lines = routes.mapIndexed { i, r ->
        Feature.fromGeometry(LineString.fromLngLats(r.coords.map { Point.fromLngLat(it.first, it.second) })).also {
            it.addNumberProperty("active", if (i == active) 1 else 0)
            it.addNumberProperty("safest", if (r.safest) 1 else 0)
        } to (i == active)
    }.sortedBy { it.second }.map { it.first } // highlighted last = drawn on top
    val lineData = FeatureCollection.fromFeatures(lines)

    val originData = FeatureCollection.fromFeatures(
        origin?.let { arrayOf(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))) } ?: emptyArray(),
    )

    val routesSource = style.getSourceAs<GeoJsonSource>(ROUTES_SOURCE_ID)
    if (routesSource != null) {
        routesSource.setGeoJson(lineData)
        style.getSourceAs<GeoJsonSource>(ORIGIN_SOURCE_ID)?.setGeoJson(originData)
        return
    }
    if (routes.isEmpty()) return

    val isActive = Expression.eq(Expression.get("active"), Expression.literal(1))
    style.addSource(GeoJsonSource(ROUTES_SOURCE_ID, lineData))
    style.addLayer(
        LineLayer(ROUTES_CASING_ID, ROUTES_SOURCE_ID).withProperties(
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineColor(Color.WHITE),
            PropertyFactory.lineWidth(Expression.switchCase(isActive, Expression.literal(9f), Expression.literal(7f))),
        ),
    )
    style.addLayer(
        LineLayer(ROUTES_LINE_ID, ROUTES_SOURCE_ID).withProperties(
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineColor(
                Expression.switchCase(
                    Expression.eq(Expression.get("safest"), Expression.literal(1)),
                    Expression.color(Color.parseColor("#1F9D55")),
                    Expression.color(Color.parseColor("#5A6677")),
                ),
            ),
            PropertyFactory.lineWidth(Expression.switchCase(isActive, Expression.literal(5f), Expression.literal(3.5f))),
            PropertyFactory.lineOpacity(Expression.switchCase(isActive, Expression.literal(1f), Expression.literal(0.6f))),
        ),
    )
    style.addSource(GeoJsonSource(ORIGIN_SOURCE_ID, originData))
    style.addLayer(
        CircleLayer(ORIGIN_LAYER_ID, ORIGIN_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(Color.parseColor("#1A73E8")),
            PropertyFactory.circleStrokeColor(Color.WHITE),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}
