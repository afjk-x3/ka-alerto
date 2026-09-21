package com.macci.kaalerto.report

import com.macci.kaalerto.data.FeatureSummary
import com.macci.kaalerto.data.haversineMeters

/** A flooded spot as the "Mga ulat" list shows it: the reducer's summary plus how far it is from this phone. */
data class ReportRow(val summary: FeatureSummary, val distanceMeters: Double?)

enum class ReportSort { NEAREST, NEWEST }

/** Distance from ([fromLat], [fromLon]) to each spot, or null for every spot when this phone has no position. */
fun reportRows(summaries: List<FeatureSummary>, fromLat: Double?, fromLon: Double?): List<ReportRow> =
    summaries.map { s ->
        ReportRow(s, if (fromLat != null && fromLon != null) haversineMeters(fromLat, fromLon, s.lat, s.lon) else null)
    }

/**
 * Nearest first (spots with no known distance after those that have one) or newest first. **Expired spots
 * always come last**, in either order: they are the ones "needing a fresh look", not the ones to act on, and
 * they must not push a live flood down the list. Ties fall back to newest, then to the feature ref, so the
 * order is the same every time the list is drawn.
 */
fun sortReportRows(rows: List<ReportRow>, sort: ReportSort): List<ReportRow> {
    val byNewest = compareByDescending<ReportRow> { it.summary.lastEventMs }
    val primary = when (sort) {
        ReportSort.NEAREST -> compareBy<ReportRow> { it.distanceMeters ?: Double.MAX_VALUE }
        ReportSort.NEWEST -> byNewest
    }
    return rows.sortedWith(
        compareBy<ReportRow> { it.summary.isStale }.then(primary).then(byNewest).thenBy { it.summary.featureRef },
    )
}

/** "120 m" under a kilometre (rounded to 10 m), "1.2 km" above it. */
fun shortDistance(meters: Double): String =
    if (meters < 1_000) "${(Math.round(meters / 10.0) * 10).toInt()} m" else "%.1f km".format(meters / 1_000)
