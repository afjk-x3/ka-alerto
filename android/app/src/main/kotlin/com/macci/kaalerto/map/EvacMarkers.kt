package com.macci.kaalerto.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.macci.kaalerto.evac.EvacState
import com.macci.kaalerto.evac.EvacStatus
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

const val EVAC_SOURCE_ID = "kaalerto-evac"
const val EVAC_LAYER_ID = "kaalerto-evac-symbols"
const val EVAC_ID_PROPERTY = "evacId"
private const val EVAC_STATUS_PROPERTY = "evacStatus"

private const val ICON_PX = 96

/**
 * BUILD_TASKS.md day 10's "distinct pins" for evacuation centres.
 *
 * Deliberately a different *shape* from the flood markers, not just a different colour:
 * flood severity is a circle, a shelter is a squared plate with a roof. Someone reading
 * this map in a storm at night should be able to tell "somewhere to go" from "something
 * to avoid" without resolving a hue — and the two are never far apart on this map.
 *
 * The pin is drawn from the *folded* status, so a centre nobody has opened yet renders
 * greyed rather than inviting.
 */
fun updateEvacMarkers(style: Style, states: List<EvacState>) {
    if (style.getImage(evacIconName(EvacStatus.ACCEPTING)) == null) {
        EvacStatus.values().forEach { status ->
            style.addImage(evacIconName(status), renderEvacIcon(status))
        }
    }

    val features = states.map { state ->
        Feature.fromGeometry(Point.fromLngLat(state.centre.lon, state.centre.lat)).apply {
            addStringProperty(EVAC_ID_PROPERTY, state.centre.id)
            addStringProperty(EVAC_STATUS_PROPERTY, state.status.key)
        }
    }
    val collection = FeatureCollection.fromFeatures(features)

    val existing = style.getSourceAs<GeoJsonSource>(EVAC_SOURCE_ID)
    if (existing != null) {
        existing.setGeoJson(collection)
        return
    }

    style.addSource(GeoJsonSource(EVAC_SOURCE_ID, collection))
    // Added before the events layer would be ideal, but MapLibre appends on addLayer and
    // the events layer may not exist yet. Flood markers are the ones you must not miss,
    // so letting them draw over a shelter pin is the right way round anyway.
    style.addLayer(
        SymbolLayer(EVAC_LAYER_ID, EVAC_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(
                Expression.match(
                    Expression.get(EVAC_STATUS_PROPERTY),
                    Expression.literal(evacIconName(EvacStatus.NOT_OPEN)),
                    *EvacStatus.values().map { status ->
                        Expression.stop(status.key, Expression.literal(evacIconName(status)))
                    }.toTypedArray(),
                ),
            ),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(0.62f),
        ),
    )
}

fun evacIconName(status: EvacStatus): String = "evac-${status.key}"

private fun renderEvacIcon(status: EvacStatus): Bitmap {
    val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val size = ICON_PX.toFloat()

    val body = Color.parseColor(
        when (status) {
            EvacStatus.ACCEPTING -> "#2E7D4F"
            EvacStatus.NEARLY_FULL -> "#E4682B"
            EvacStatus.NOT_OPEN -> "#8A939B"
        },
    )

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = body
    }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = size * 0.055f
        color = Color.WHITE
    }
    val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = size * 0.06f
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    val inset = size * 0.16f
    canvas.drawRect(inset, inset, size - inset, size - inset, fill)
    canvas.drawRect(inset, inset, size - inset, size - inset, ring)

    // A roof over an open doorway.
    val roof = Path().apply {
        moveTo(size * 0.30f, size * 0.50f)
        lineTo(size * 0.50f, size * 0.32f)
        lineTo(size * 0.70f, size * 0.50f)
    }
    canvas.drawPath(roof, glyph)
    canvas.drawLine(size * 0.37f, size * 0.50f, size * 0.37f, size * 0.68f, glyph)
    canvas.drawLine(size * 0.63f, size * 0.50f, size * 0.63f, size * 0.68f, glyph)
    canvas.drawLine(size * 0.37f, size * 0.68f, size * 0.63f, size * 0.68f, glyph)

    return bitmap
}
