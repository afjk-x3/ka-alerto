package com.macci.kaalerto.data

import kotlin.math.exp
import kotlin.math.sqrt

/** Everything the map and detail sheet need to render one flooded spot. */
data class FeatureSummary(
    val featureRef: String,
    val lat: Double,
    val lon: Double,
    /** S0..S3, or "SX" for Rule C conflict. */
    val severity: String,
    val confidence: Double,
    val bucket: String,
    val isConflicted: Boolean,
    val lastEventMs: Long,
    val isStale: Boolean,
    val confirmCount: Int,
    val disputeCount: Int,
    /** Full history for this feature, most recent first — the detail sheet's list. */
    val events: List<Event>,
)

private const val PROXIMATE_METERS = 500.0
private const val WEIGHT_FLOOR = 0.05
private const val CONFLICT_WEIGHT_THRESHOLD = 0.5
private const val DEESCALATION_COUNT = 2

private data class Weighted(val event: Event, val severity: String, val weight: Double, val proximate: Boolean)
private data class Resolution(val severity: String, val conflicted: Boolean, val bucket: String, val confidence: Double)

/**
 * Pure fold over one feature's events into a display state — `state(feature, now) =
 * reduce(events_for(feature), now)` (docs/03-architecture.md §4.1). Deliberately a
 * simplified reading of that section: role-based weight instead of signed reputation
 * certificates (no crypto in this build, per ground rule 4), no independence/sybil
 * discounting, no sensor tier. BUILD_TASKS.md day 4's own literal spec is "weight =
 * role × proximity × exponential time decay" plus Rules B and C — this implements
 * exactly that, using docs/03-architecture.md §5.2-5.3's concrete formulas (proximity
 * bands, decay, Wilson score confidence) where they don't require that missing
 * infrastructure.
 */
object Reducer {

    fun summarize(featureRef: String, allEvents: List<Event>, now: Long): FeatureSummary? {
        val events = allEvents.filter { it.featureRef == featureRef }
        if (events.isEmpty()) return null

        // docs/03-architecture.md §4.3 step 4: one live position per author, so nobody
        // stacks repeated confirms to fake consensus.
        val latestPerAuthor = events.groupBy { it.authorId }.map { (_, authored) -> authored.maxBy { it.timestampMs } }

        // Anchor point for proximity: the original reports' centroid, not every
        // confirm/dispute — a confirm/dispute's own lat/lon is the CONFIRMING device's
        // location, not the flood's, so it must not shift where "the feature" is.
        val reports = events.filter { it.type == "flood_report" }
        val anchorLat = reports.map { it.lat }.average().takeUnless { it.isNaN() } ?: events.first().lat
        val anchorLon = reports.map { it.lon }.average().takeUnless { it.isNaN() } ?: events.first().lon

        val weighted = latestPerAuthor.mapNotNull { event ->
            val severity = event.severity ?: return@mapNotNull null
            val ageMs = (now - event.timestampMs).coerceAtLeast(0)
            val distance = haversineMeters(anchorLat, anchorLon, event.lat, event.lon)
            val proximity = proximityFactor(distance)
            val weight = roleWeight(event.authorRole) * proximity * recencyFactor(ageMs, severity)
            Weighted(event, severity, weight, proximity >= 0.7)
        }
        val weightBySeverity = weighted.groupBy { it.severity }.mapValues { (_, ws) -> ws.sumOf { it.weight } }

        // Rule D — an official event overrides everything else outright.
        val officialEvent = events.filter { it.authorRole == "official" && it.severity != null }.maxByOrNull { it.timestampMs }

        val resolution = if (officialEvent != null) {
            resolveOfficial(officialEvent, weightBySeverity)
        } else {
            resolveCrowd(weighted, weightBySeverity)
        }

        return FeatureSummary(
            featureRef = featureRef,
            lat = anchorLat,
            lon = anchorLon,
            severity = resolution.severity,
            confidence = resolution.confidence,
            bucket = resolution.bucket,
            isConflicted = resolution.conflicted,
            lastEventMs = events.maxOf { it.timestampMs },
            isStale = now > events.maxOf { it.expiresAt },
            confirmCount = events.count { it.type == "confirm" },
            disputeCount = events.count { it.type == "dispute" },
            events = events.sortedByDescending { it.timestampMs },
        )
    }

    fun summarizeAll(allEvents: List<Event>, now: Long): List<FeatureSummary> =
        allEvents.mapNotNull { it.featureRef }.distinct().mapNotNull { summarize(it, allEvents, now) }

    private fun resolveOfficial(officialEvent: Event, weightBySeverity: Map<String, Double>): Resolution {
        val severity = officialEvent.severity!!
        val total = weightBySeverity.values.sum()
        val agree = weightBySeverity[severity] ?: 0.0
        return Resolution(severity, conflicted = false, bucket = "official", confidence = if (total > 0) agree / total else 1.0)
    }

    private fun resolveCrowd(weighted: List<Weighted>, weightBySeverity: Map<String, Double>): Resolution {
        val severitiesWithWeight = weightBySeverity.filterValues { it > WEIGHT_FLOOR }.keys
        if (severitiesWithWeight.isEmpty()) return Resolution("S0", conflicted = false, bucket = "unverified", confidence = 0.0)

        // Rule A: the highest claimed tier is the ceiling — a single credible higher
        // report always wins over any number of older/lower ones.
        val maxSeverity = severitiesWithWeight.maxBy { severityOrdinal(it) }

        // Rule B: only a lower tier with >=2 proximate observers can pull the display
        // down from that high-water mark.
        val lowerEligible = severitiesWithWeight
            .filter { severityOrdinal(it) < severityOrdinal(maxSeverity) }
            .filter { sev -> weighted.count { it.severity == sev && it.proximate } >= DEESCALATION_COUNT }

        val dangerWeight = (weightBySeverity["S2"] ?: 0.0) + (weightBySeverity["S3"] ?: 0.0)
        val safeWeight = (weightBySeverity["S0"] ?: 0.0) + (weightBySeverity["S1"] ?: 0.0)
        // Rule C: genuine disagreement is shown, not resolved by count or averaging —
        // but a met de-escalation bar (Rule B) is a real resolution, so it pre-empts SX.
        val conflicted = lowerEligible.isEmpty() && dangerWeight >= CONFLICT_WEIGHT_THRESHOLD && safeWeight >= CONFLICT_WEIGHT_THRESHOLD

        val resolvedSeverity = when {
            conflicted -> "SX"
            lowerEligible.isNotEmpty() -> lowerEligible.maxBy { severityOrdinal(it) }
            else -> maxSeverity
        }

        if (conflicted) return Resolution(resolvedSeverity, conflicted = true, bucket = "unverified", confidence = 0.0)

        // docs/03-architecture.md §5.3: "Unverified: confidence < 0.35, OR a single
        // report" — the OR matters, since a lone report's Wilson bound can round to
        // just above 0.35 depending on weight; a single observer must never read as
        // more than Unverified regardless.
        if (weighted.size <= 1) return Resolution(resolvedSeverity, conflicted = false, bucket = "unverified", confidence = weightBySeverity[resolvedSeverity]?.let { wilsonLowerBound(it, 0.0) } ?: 0.0)

        val agree = weightBySeverity[resolvedSeverity] ?: 0.0
        val disagree = weightBySeverity.filterKeys { it != resolvedSeverity }.values.sum()
        val confidence = wilsonLowerBound(agree, disagree)
        val proximateAgreeing = weighted.count { it.severity == resolvedSeverity && it.proximate }
        val bucket = when {
            confidence >= 0.65 && proximateAgreeing >= 2 -> "confirmed"
            confidence >= 0.35 -> "likely"
            else -> "unverified"
        }
        return Resolution(resolvedSeverity, conflicted = false, bucket = bucket, confidence = confidence)
    }
}

private fun roleWeight(role: String): Double = when (role) {
    "official" -> 5.0
    "responder" -> 2.5
    else -> 1.0
}

/** docs/03-architecture.md §5.2 proximity table, collapsed to the bands day 4 needs. */
private fun proximityFactor(distanceMeters: Double): Double = when {
    distanceMeters <= 100.0 -> 1.0
    distanceMeters <= PROXIMATE_METERS -> 0.7
    distanceMeters <= 2000.0 -> 0.4
    else -> 0.2
}

private fun recencyFactor(ageMs: Long, severity: String): Double {
    val ageMinutes = ageMs / 60_000.0
    return exp(-ageMinutes / decayTauMinutesFor(severity))
}

/** docs/03-architecture.md §5.3 — Wilson score lower bound on weighted counts, z≈1.44 (~85%). */
private fun wilsonLowerBound(agree: Double, disagree: Double): Double {
    val n = agree + disagree
    if (n <= 0.0) return 0.0
    val z = 1.44
    val z2 = z * z
    val pHat = agree / n
    val numerator = pHat + z2 / (2 * n) - z * sqrt(pHat * (1 - pHat) / n + z2 / (4 * n * n))
    val denominator = 1 + z2 / n
    return (numerator / denominator).coerceIn(0.0, 1.0)
}
