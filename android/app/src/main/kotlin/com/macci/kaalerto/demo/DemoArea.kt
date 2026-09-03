package com.macci.kaalerto.demo

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

/**
 * The frozen demo area: **Barangay San Juan Bautista, San Nicolas, Ilocos Norte.**
 *
 * Frozen 3 Sep 2026. Every fixture, screenshot and route lives inside [bounds].
 *
 * ## Provenance — read before changing any coordinate here
 *
 * There is no official boundary polygon for this barangay in OpenStreetMap (only a
 * labelled point, `place=quarter`) or in anything else fetched for this project. What
 * follows is built from two independent centroid sources that agree within ~300 m, plus
 * real, named streets and public buildings from OSM within a bounding box around that
 * centroid — not an authoritative barangay boundary.
 *
 * - PhilAtlas barangay profile: 18.1709, 120.6058
 *   https://www.philatlas.com/luzon/r01/ilocos-norte/san-nicolas/san-juan-bautista.html
 * - OSM Nominatim (`place=quarter` node, id 12568180056): 18.1720304, 120.6029913
 *
 * [bounds] is deliberately narrower than it could be: San Nicolas' municipal hall,
 * cultural centre and both named hospitals cluster about 1.3 km west of this centroid
 * (around 120.593–120.598), which is very plausibly a *different* barangay's poblacion.
 * With no boundary polygon to confirm either way, they are left out rather than risk
 * attributing another barangay's landmarks to this one. Everything inside [bounds] —
 * three schools, one clinic, two real streets — was confirmed by name against OSM before
 * being used anywhere in the fixtures (seed reports, evacuation centres, routes).
 *
 * **This has not been walked or verified against a printed barangay map.** Before this
 * goes in front of the barangay itself, someone who knows the area should confirm the
 * boundary and the evacuation-centre list.
 */
object DemoArea {

    val bounds: LatLngBounds = LatLngBounds.Builder()
        .include(LatLng(18.1760, 120.6130))  // north-east
        .include(LatLng(18.1660, 120.5990))  // south-west
        .build()

    /** PhilAtlas centroid — see class doc. */
    val centre: LatLng = LatLng(18.1709, 120.6058)

    /**
     * Zoom envelope for the offline pack.
     *
     * The floor is deliberately not 0: pre-downloading the whole world at low zoom is a
     * large download for tiles nobody pans to. The ceiling is where street names and
     * road geometry stop improving for this use — past 16 the pack grows fast and the
     * extra detail does not change whether a road reads as passable.
     */
    const val MIN_ZOOM = 10.0
    const val MAX_ZOOM = 16.0

    /** Camera zoom on first open. */
    const val INITIAL_ZOOM = 15.0

    /**
     * MapLibre's free demo style. No API key, no account, no per-load cost.
     *
     * Detail is coarse — it exists to prove the offline pipeline, not to ship. Replace
     * with a self-hosted OSM-derived style before the demo; the architecture doc calls
     * for OSM-derived vector tiles and rejects commercial SDKs on both cost and
     * offline-capability grounds (docs/03-architecture.md §519-521).
     */
    const val STYLE_URL = "https://demotiles.maplibre.org/style.json"

    /** Identifies our region among any others in MapLibre's offline database. */
    const val REGION_NAME = "kaalerto-demo-area"
}
