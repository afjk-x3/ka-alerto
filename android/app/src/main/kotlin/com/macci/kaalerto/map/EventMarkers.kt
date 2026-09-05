package com.macci.kaalerto.map

import android.graphics.Color
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.ui.theme.SeverityColors
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val EVENTS_SOURCE_ID = "kaalerto-events"
private const val EVENTS_LAYER_ID = "kaalerto-events-circles"

/**
 * Renders one marker per event, colored by severity.
 *
 * This is deliberately not the feature-level reducer (BUILD_TASKS.md day 4) — every
 * report gets its own dot, so the seeded conflicting pair shows as two overlapping
 * markers rather than one resolved SX state, which is correct for what's built so far.
 */
fun updateEventMarkers(style: Style, events: List<Event>) {
    val features = events.map { event ->
        Feature.fromGeometry(Point.fromLngLat(event.lon, event.lat)).apply {
            addStringProperty("severity", event.severity ?: "unknown")
        }
    }
    val collection = FeatureCollection.fromFeatures(features)

    val existingSource = style.getSourceAs<GeoJsonSource>(EVENTS_SOURCE_ID)
    if (existingSource != null) {
        existingSource.setGeoJson(collection)
        return
    }

    style.addSource(GeoJsonSource(EVENTS_SOURCE_ID, collection))
    style.addLayer(
        CircleLayer(EVENTS_LAYER_ID, EVENTS_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(
                Expression.match(
                    Expression.get("severity"),
                    Expression.color(Color.parseColor(SeverityColors.UNKNOWN)),
                    Expression.stop("S0", Expression.color(Color.parseColor(SeverityColors.S0))),
                    Expression.stop("S1", Expression.color(Color.parseColor(SeverityColors.S1))),
                    Expression.stop("S2", Expression.color(Color.parseColor(SeverityColors.S2))),
                    Expression.stop("S3", Expression.color(Color.parseColor(SeverityColors.S3))),
                ),
            ),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleStrokeColor(Color.WHITE),
        ),
    )
}
