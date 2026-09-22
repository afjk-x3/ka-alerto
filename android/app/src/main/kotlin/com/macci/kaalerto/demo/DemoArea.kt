package com.macci.kaalerto.demo

import com.macci.kaalerto.data.haversineMeters
import kotlin.math.roundToInt
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

/**
 * The curated area: **Brgy. Poblacion, Mapandan, Pangasinan.**
 *
 * Repointed here 22 Sep 2026 from the original frozen area (Barangay San Juan Bautista, San
 * Nicolas, Ilocos Norte, frozen 3 Sep 2026), at the user's request to widen real GPS/offline
 * coverage to the whole of Pangasinan — see [PANGASINAN_NORTH] etc. below. **San Juan
 * Bautista's fixtures (seed reports, evacuation centres, routes) are retained, not deleted**
 * — they simply stop being inside [bounds] or covered by an offline pack. `REGION_NAME`
 * changed for exactly this reason: a device with the old pack on disk must download a fresh
 * one for the new bounds, not silently keep serving San Juan Bautista tiles under a name
 * that now means something else.
 *
 * ## Provenance — read before changing any coordinate here
 *
 * - OSM Nominatim (`boundary=administrative` relation 13308922, "Mapandan"): bounding box
 *   15.9901890–16.0450711 N, 120.4192167–120.4874774 E; centroid 16.0269118, 120.4537476.
 * - Bundled `assets/psgc.json` independently lists "Poblacion" as one of Mapandan's real
 *   barangays, confirming the OSM data (`addr:quarter=Poblacion` on the school node below)
 *   rather than assuming it.
 *
 * [bounds] widens the raw OSM envelope slightly on the west side (120.410 instead of
 * 120.4192) so it comfortably includes Santa Barbara-Mangaldan Road and the real GPS
 * fixes recorded near it during testing — both sit right at, and occasionally just past,
 * the administrative relation's own boundary, which is normal for a road that continues
 * into the next town. This is *wider* than the sourced boundary, the opposite adjustment
 * from San Juan Bautista's (which was narrowed to avoid attributing a neighbouring
 * barangay's landmarks) — noted here for the same reason: say which way and why.
 *
 * Everything inside [bounds] used anywhere in the fixtures — Mapandan Catholic School,
 * Santa Barbara-Mangaldan Road, Pandan Avenue — was fetched from OpenStreetMap on 22 Sep
 * 2026 (via the `overpass.kumi.systems` mirror; the primary `overpass-api.de` refuses this
 * dev machine's requests with a 406) and confirmed by name, the same standard San Juan
 * Bautista's fixtures were held to.
 *
 * **This has not been walked or verified against a printed barangay map**, same caveat as
 * before it moved.
 */
object DemoArea {

    /** Header display name for the map screen — matches the curated area above. */
    const val BARANGAY_NAME = "Brgy. Poblacion"

    /** The town and province, for place labels outside the header. */
    const val MUNICIPALITY = "Mapandan, Pangasinan"

    // Plain constants as well as [bounds], so [isInDemoArea] can be unit-tested without
    // touching MapLibre classes (const reads are inlined; they never initialise this object).
    const val NORTH = 16.045
    const val SOUTH = 15.990
    const val EAST = 120.487
    const val WEST = 120.410
    const val CENTRE_LAT = 16.0256546 // Mapandan Catholic School, Brgy. Poblacion (OSM node 12368141806)
    const val CENTRE_LON = 120.4544745

    val bounds: LatLngBounds = LatLngBounds.Builder()
        .include(LatLng(NORTH, EAST))
        .include(LatLng(SOUTH, WEST))
        .build()

    /** Mapandan Catholic School — see class doc. */
    val centre: LatLng = LatLng(CENTRE_LAT, CENTRE_LON)

    /**
     * The whole of Pangasinan province, for real offline map coverage — not a content area:
     * nothing outside [bounds] has a curated seed report, evacuation centre or gazetteer
     * entry, so a resident elsewhere in the province sees a real map with no local reports,
     * which is honest (no report near you is not the same claim as no flood near you) rather
     * than wrong (this app conflated the two into one flag before 22 Sep 2026 — see
     * [isInOfflineCoverage]'s own doc).
     *
     * Source: OSM Nominatim (`boundary=administrative` relation 1504701, "Pangasinan"),
     * bounding box 15.6179953–16.5788824 N, 119.6095165–120.9202823 E. This rectangle is
     * markedly bigger than the province's actual land area (PhilAtlas: 5,450.59 km²,
     * https://www.philatlas.com/luzon/r01/pangasinan.html) because the coastline along
     * Lingayen Gulf makes the true shape very irregular; a rectangle is all
     * `OfflineTilePyramidRegionDefinition` can express. Measured real cost of downloading
     * this whole rectangle at [MIN_ZOOM]–[MAX_ZOOM]: 2,349 tiles, ~17.7 MB (~23 MB including
     * style/fonts/sprites), under 90 seconds on a home connection (spiked 22 Sep 2026) — far
     * below the naive per-km² extrapolation from a small dense-urban pack, because most of a
     * rural province's tiles are the coarse low-zoom ones.
     */
    const val PANGASINAN_NORTH = 16.5788824
    const val PANGASINAN_SOUTH = 15.6179953
    const val PANGASINAN_EAST = 120.9202823
    const val PANGASINAN_WEST = 119.6095165

    val pangasinanBounds: LatLngBounds = LatLngBounds.Builder()
        .include(LatLng(PANGASINAN_NORTH, PANGASINAN_EAST))
        .include(LatLng(PANGASINAN_SOUTH, PANGASINAN_WEST))
        .build()

    /**
     * Zoom envelope for both offline packs.
     *
     * The floor is deliberately not 0: pre-downloading the whole world at low zoom is a
     * large download for tiles nobody pans to. The ceiling matches OpenFreeMap's actual
     * maxzoom (14) — requesting tiles past what the source has doesn't crash anything,
     * but it's pointless pack size for zero extra detail. MapLibre over-zooms z14 tiles
     * to render at higher camera zoom automatically.
     */
    const val MIN_ZOOM = 10.0
    const val MAX_ZOOM = 14.0

    /** Camera zoom on first open. */
    const val INITIAL_ZOOM = 15.0

    /**
     * OpenFreeMap's "Liberty" style. No API key, no account, no per-load cost, planet
     * coverage at up to z14 (verified 3 Sep 2026: https://tiles.openfreemap.org/planet).
     *
     * **Not `demotiles.maplibre.org` — that domain is unusable for offline packing.**
     * It hits a confirmed, open, unfixed upstream bug: MapLibre Native's offline
     * resource-matching code (`createTokenMap`, in `mapbox.cpp`) fails to escape the
     * `{fontstack}`/`{range}` placeholders in a glyphs URL template before compiling it
     * as a regex, and demotiles.maplibre.org's glyphs endpoint hits exactly that path —
     * `std::regex_error: invalid range in a {} expression`, an uncaught native
     * exception that SIGABRTs the whole process. It doesn't even require
     * `createOfflineRegion()`: merely calling `OfflineManager.listOfflineRegions()`
     * while that style is active is enough to crash, confirmed by isolating it on-device
     * (3 Sep 2026). See https://github.com/maplibre/maplibre-native/issues/4403 — the
     * maintainers' own stated workaround is "host your glyphs anywhere that is not
     * demotiles.maplibre.org," naming OpenFreeMap by name. Do not switch back to it.
     *
     * This is still not the self-hosted OSM-derived style the architecture doc calls
     * for (docs/03-architecture.md §519-521) — that remains a pre-demo task — but it is
     * detailed enough to actually exercise the offline pipeline, unlike the old style's
     * maxzoom-6 tileset, and it doesn't crash.
     */
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    /**
     * Identifies the curated-area pack among any others in MapLibre's offline database.
     * Changed 22 Sep 2026 (was "kaalerto-demo-area") when this object repointed to
     * Mapandan — see the class doc's second paragraph for why a fresh name, not a reused
     * one, was the safe choice.
     */
    const val REGION_NAME = "kaalerto-mapandan-area"
}

/** Inside [DemoArea.bounds] — the only area with curated seed reports, evacuation centres and gazetteer entries. */
fun isInDemoArea(lat: Double, lon: Double): Boolean =
    lat in DemoArea.SOUTH..DemoArea.NORTH && lon in DemoArea.WEST..DemoArea.EAST

/**
 * Inside [DemoArea.bounds] **or** [DemoArea.pangasinanBounds] — real offline map tiles exist
 * here, whether or not there is any curated content. Kept separate from [isInDemoArea] on
 * purpose (added 22 Sep 2026): before this split, one flag answered "is there a gazetteer
 * entry here" and "is this covered offline" at once, so widening it to cover real Pangasinan
 * tiles would have made the gazetteer confidently mislabel a point in, say, Dagupan as
 * "Brgy. Poblacion, Mapandan, Pangasinan". Use this for offline-coverage and home-pack
 * decisions; use [isInDemoArea] for anything that names a specific place.
 */
fun isInOfflineCoverage(lat: Double, lon: Double): Boolean =
    isInDemoArea(lat, lon) ||
        (lat in DemoArea.PANGASINAN_SOUTH..DemoArea.PANGASINAN_NORTH && lon in DemoArea.PANGASINAN_WEST..DemoArea.PANGASINAN_EAST)

/** Straight-line distance from the curated area's centre, in whole kilometres, never below 1. */
fun kmFromDemoArea(lat: Double, lon: Double): Int =
    (haversineMeters(lat, lon, DemoArea.CENTRE_LAT, DemoArea.CENTRE_LON) / 1000).roundToInt().coerceAtLeast(1)
