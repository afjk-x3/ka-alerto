package com.macci.kaalerto.map

import android.graphics.Color
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val PICKED_LOCATION_SOURCE_ID = "kaalerto-picked-location"
private const val PICKED_LOCATION_LAYER_ID = "kaalerto-picked-location-circle"

/**
 * The draft pin shown while pick-mode is active (map/MapScreen.kt). A tap moves this;
 * nothing is committed to the caller until the confirm bar's "I-save" is tapped — see
 * the pick-mode click-listener comment in MapScreen.kt for why a tap used to commit
 * immediately and why that was wrong. Pass `latLng == null` to clear it.
 */
fun updatePickedLocationMarker(style: Style, latLng: LatLng?) {
    val collection = if (latLng == null) {
        FeatureCollection.fromFeatures(emptyArray())
    } else {
        FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(Point.fromLngLat(latLng.longitude, latLng.latitude))))
    }

    val existingSource = style.getSourceAs<GeoJsonSource>(PICKED_LOCATION_SOURCE_ID)
    if (existingSource != null) {
        existingSource.setGeoJson(collection)
        return
    }
    if (latLng == null) return // nothing to draw yet, and nothing was drawn before

    style.addSource(GeoJsonSource(PICKED_LOCATION_SOURCE_ID, collection))
    val layer = CircleLayer(PICKED_LOCATION_LAYER_ID, PICKED_LOCATION_SOURCE_ID).withProperties(
        PropertyFactory.circleRadius(10f),
        PropertyFactory.circleColor(Color.parseColor("#D6304A")),
        PropertyFactory.circleStrokeColor(Color.WHITE),
        PropertyFactory.circleStrokeWidth(3f),
    )
    style.addLayer(layer)
}
