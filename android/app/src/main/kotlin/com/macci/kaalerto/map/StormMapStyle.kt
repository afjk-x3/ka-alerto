package com.macci.kaalerto.map

import android.graphics.Color
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer

/**
 * Day 5's Storm Mode, applied to the map surface as well as the chrome.
 *
 * The toggle used to invert the header, filter bar, legend and action bar and leave the
 * map itself a bright white rectangle filling roughly 60% of the screen — in the one mode
 * built for a dark night and a dying battery.
 *
 * **This restyles the already-loaded style in memory rather than loading a dark one.**
 * A second style URL would be the obvious fix and is the wrong one here: MapLibre's
 * offline packs are scoped to a style, so a dark style means a second pack to
 * pre-download, and a resident who downloaded only the light one would get a blank map
 * the moment they declared Storm — the exact failure the whole offline gate exists to
 * prevent. Re-tinting what is already cached costs nothing and cannot fail offline.
 *
 * **Our own layers are never touched.** Everything the app draws is prefixed
 * [OURS_PREFIX], and severity colour is constant across Normal and Storm by decision
 * (`ui/theme/SeverityColors.kt`) — a colour meaning "impassable" cannot shift with a
 * display setting. So the basemap goes dark underneath and the markers stay exactly as
 * bright as they were, which makes them *more* legible here rather than less.
 *
 * Ported from 21fc72e on feat/event-sourced-roles.
 */
private const val OURS_PREFIX = "kaalerto-"

// Deliberately flat, low-chroma tones. This is a night map to read a street off, not a
// dark theme to admire: contrast goes to the markers, not to the basemap.
private val STORM_BACKGROUND = Color.parseColor("#10151A")
private val STORM_LAND = Color.parseColor("#171D23")
private val STORM_BUILDING = Color.parseColor("#212932")
private val STORM_GREEN = Color.parseColor("#15211B")
private val STORM_WATER = Color.parseColor("#0C2634")
private val STORM_ROAD = Color.parseColor("#3A444E")
private val STORM_ROAD_MAJOR = Color.parseColor("#4E5A66")
private val STORM_LINE = Color.parseColor("#252D35")
private val STORM_TEXT = Color.parseColor("#9BA6B0")
private val STORM_TEXT_HALO = Color.parseColor("#0A0E12")

/**
 * Applied on every style load, so toggling the mode reloads the style and re-tints it.
 * Reloading is what makes this reversible without having to read back and stash the
 * original paint values, which in a real style are expressions rather than plain colours.
 */
fun applyStormTint(style: Style, storm: Boolean) {
    if (!storm) return
    style.layers.forEach { layer ->
        if (layer.id.startsWith(OURS_PREFIX)) return@forEach
        val id = layer.id.lowercase()
        runCatching {
            when (layer) {
                is BackgroundLayer ->
                    layer.setProperties(PropertyFactory.backgroundColor(STORM_BACKGROUND))

                is FillLayer ->
                    layer.setProperties(PropertyFactory.fillColor(fillColorFor(id)))

                is FillExtrusionLayer ->
                    layer.setProperties(PropertyFactory.fillExtrusionColor(STORM_BUILDING))

                is LineLayer ->
                    layer.setProperties(PropertyFactory.lineColor(lineColorFor(id)))

                // Icon opacity as well as text colour: the basemap's own POI sprites
                // (fuel stations, shops) are full-brightness brand colours and read as
                // loud as a severity marker once everything around them is dark. Ours
                // are never reached here, so this cannot dim a flood marker.
                is SymbolLayer -> layer.setProperties(
                    PropertyFactory.textColor(STORM_TEXT),
                    PropertyFactory.textHaloColor(STORM_TEXT_HALO),
                    PropertyFactory.iconOpacity(0.45f),
                )

                else -> Unit
            }
        }
        // A style we do not control can carry layer types and ids we have not seen.
        // One unexpected layer must not abort the pass and leave the map half-tinted,
        // so failures are swallowed per layer rather than per style.
    }
}

/**
 * Category is guessed from the layer id, because a vector style's own paint is usually a
 * data-driven expression rather than a colour we could darken arithmetically. The
 * fallback is land, which is the safe wrong answer: an unrecognised fill reads as ground
 * rather than as water, and mistaking ground for water on a flood map is the error that
 * matters.
 */
private fun fillColorFor(id: String): Int = when {
    id.contains("water") || id.contains("ocean") || id.contains("sea") -> STORM_WATER
    id.contains("building") -> STORM_BUILDING
    id.contains("park") || id.contains("wood") || id.contains("forest") ||
        id.contains("grass") || id.contains("landcover") -> STORM_GREEN
    else -> STORM_LAND
}

private fun lineColorFor(id: String): Int = when {
    id.contains("water") || id.contains("river") || id.contains("stream") -> STORM_WATER
    id.contains("motorway") || id.contains("trunk") || id.contains("primary") -> STORM_ROAD_MAJOR
    id.contains("road") || id.contains("highway") || id.contains("street") ||
        id.contains("transportation") || id.contains("bridge") || id.contains("tunnel") -> STORM_ROAD
    else -> STORM_LINE
}
