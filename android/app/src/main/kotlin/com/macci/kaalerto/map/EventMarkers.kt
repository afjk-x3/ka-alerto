package com.macci.kaalerto.map

import com.macci.kaalerto.data.FeatureSummary
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

const val EVENTS_SOURCE_ID = "kaalerto-events"
const val EVENTS_LAYER_ID = "kaalerto-events-symbols"
const val FEATURE_REF_PROPERTY = "featureRef"
private const val MARKER_KIND_PROPERTY = "markerKind"

/**
 * Renders one marker per feature (day 4's reducer output), not one per raw event —
 * BUILD_TASKS.md day 4 DoD: "the seeded conflicting pair renders as SX." Icons (not flat
 * circles) per [MarkerKind] — matching design/artboards/Map-Normal.dc.html, which draws
 * a distinct ring style + glyph per state rather than colour alone.
 */
fun updateEventMarkers(style: Style, summaries: List<FeatureSummary>) {
    if (style.getImage(markerIconName(MarkerKind.S0)) == null) {
        registerMarkerIcons(style)
    }

    val features = summaries.map { summary ->
        Feature.fromGeometry(Point.fromLngLat(summary.lon, summary.lat)).apply {
            addStringProperty(FEATURE_REF_PROPERTY, summary.featureRef)
            addStringProperty(MARKER_KIND_PROPERTY, MarkerKind.forSummary(summary).key)
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
        SymbolLayer(EVENTS_LAYER_ID, EVENTS_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(
                Expression.match(
                    Expression.get(MARKER_KIND_PROPERTY),
                    Expression.literal(markerIconName(MarkerKind.S0)),
                    *MarkerKind.entries.map { kind ->
                        Expression.stop(kind.key, Expression.literal(markerIconName(kind)))
                    }.toTypedArray(),
                ),
            ),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(0.7f),
        ),
    )
}
