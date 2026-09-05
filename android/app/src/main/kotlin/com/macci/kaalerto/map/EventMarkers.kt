package com.macci.kaalerto.map

import android.graphics.Color
import com.macci.kaalerto.data.FeatureSummary
import com.macci.kaalerto.ui.theme.SeverityColors
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

const val EVENTS_SOURCE_ID = "kaalerto-events"
const val EVENTS_LAYER_ID = "kaalerto-events-circles"
const val FEATURE_REF_PROPERTY = "featureRef"

/**
 * Renders one marker per feature (day 4's reducer output), not one per raw event —
 * BUILD_TASKS.md day 4 DoD: "the seeded conflicting pair renders as SX." A stale
 * feature is shown desaturated (lower circle opacity) with a dashed-look ring
 * (thicker, lighter stroke) rather than a solid one, per the day 4 "Stale" bullet.
 */
fun updateEventMarkers(style: Style, summaries: List<FeatureSummary>) {
    val features = summaries.map { summary ->
        Feature.fromGeometry(Point.fromLngLat(summary.lon, summary.lat)).apply {
            addStringProperty(FEATURE_REF_PROPERTY, summary.featureRef)
            addStringProperty("severity", summary.severity)
            addBooleanProperty("isConflicted", summary.isConflicted)
            addBooleanProperty("isStale", summary.isStale)
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
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(
                Expression.match(
                    Expression.get("severity"),
                    Expression.color(Color.parseColor(SeverityColors.UNKNOWN)),
                    Expression.stop("S0", Expression.color(Color.parseColor(SeverityColors.S0))),
                    Expression.stop("S1", Expression.color(Color.parseColor(SeverityColors.S1))),
                    Expression.stop("S2", Expression.color(Color.parseColor(SeverityColors.S2))),
                    Expression.stop("S3", Expression.color(Color.parseColor(SeverityColors.S3))),
                    Expression.stop("SX", Expression.color(Color.parseColor(SeverityColors.SX))),
                ),
            ),
            // Stale reads as faded, per BUILD_TASKS.md day 4's "Stale: desaturated".
            PropertyFactory.circleOpacity(
                Expression.switchCase(Expression.get("isStale"), Expression.literal(0.45f), Expression.literal(1f)),
            ),
            PropertyFactory.circleStrokeWidth(
                Expression.switchCase(Expression.get("isConflicted"), Expression.literal(3f), Expression.literal(1.5f)),
            ),
            PropertyFactory.circleStrokeColor(Color.WHITE),
        ),
    )
}
