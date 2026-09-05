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
    /**
     * The severity an official has posted, whether or not it is currently in force.
     * Non-null and *not* equal to [severity] means the second-official gate is holding
     * it back — see [pendingSecondOfficial].
     */
    val officialSeverity: String? = null,
    /** Who posted it, and when. An official action is never anonymous (OfficialVerify.dc.html). */
    val officialAuthorName: String? = null,
    val officialAtMs: Long? = null,
    /**
     * An official has asked to lower a contradicted spot and is waiting for a second
     * official to agree. Day 10's gate — see [REQUIRED_OFFICIALS_TO_DEESCALATE].
     */
    val pendingSecondOfficial: Boolean = false,
    /**
     * How many residents' latest reports are *worse* than the official severity. Drives
     * OfficialReverse.dc.html's "3 residente ang salungat dito" and the detail sheet's
     * "N residents report worse conditions than the official status".
     */
    val contradictingCount: Int = 0,
)

private const val PROXIMATE_METERS = 500.0
private const val WEIGHT_FLOOR = 0.05
private const val CONFLICT_WEIGHT_THRESHOLD = 0.5
private const val DEESCALATION_COUNT = 2

/**
 * Day 10's second-official gate (OfficialReverse.dc.html: "Magkasalungat ang lugar na
 * ito, kaya hindi kayang ibaba ng iisang opisyal ang severity").
 *
 * It is the same safety asymmetry the crowd path already runs on — raising a severity
 * takes one credible voice, lowering one takes corroboration — extended to officials so
 * that a single person cannot quietly overrule residents who are standing in the water.
 * It applies **only** when lowering, and **only** when at least [DEESCALATION_COUNT]
 * residents are currently reporting worse. Raising a severity, and reversing another
 * official's clearance, stay single-official and immediate.
 */
private const val REQUIRED_OFFICIALS_TO_DEESCALATE = 2

private data class Weighted(val event: Event, val severity: String, val weight: Double, val proximate: Boolean)
private data class Resolution(
    val severity: String,
    val conflicted: Boolean,
    val bucket: String,
    val confidence: Double,
    /** The highest severity any weighted report claims, before Rules B/C resolve it. */
    val maxSeverity: String = severity,
)

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
        // The crowd is the crowd: an official's own ruling must not also be counted as
        // a report *within* the crowd it is overriding. Leaving it in made a lone
        // official clearance read as a two-sided disagreement and rendered SX — the
        // official arguing with the residents, at role weight 5.
        val crowdWeighted = weighted.filter { it.event.authorRole != "official" }
        val weightBySeverity = crowdWeighted.groupBy { it.severity }.mapValues { (_, ws) -> ws.sumOf { it.weight } }

        val crowd = resolveCrowd(crowdWeighted, weightBySeverity)

        // Rule D — an official event overrides the crowd, with day 10's one exception.
        val officialEvents = events.filter { it.authorRole == "official" && it.severity != null }
        val latestOfficial = officialEvents.maxByOrNull { it.timestampMs }
        val officialSeverity = latestOfficial?.severity

        // Residents currently saying it is *worse* than the official line. Officials are
        // excluded: this counts the people being overruled, not the people overruling.
        // "Still live" is the reducer's existing notion: weight above the floor, which
        // is time decay doing the work. Yesterday's reports have decayed away and do not
        // hold an all-clear hostage; people reporting worse right now do.
        val contradicting = officialSeverity?.let { official ->
            crowdWeighted.count {
                severityOrdinal(it.severity) > severityOrdinal(official) && it.weight > WEIGHT_FLOOR
            }
        } ?: 0

        // The gate only ever holds back a de-escalation of a contradicted spot. Raising a
        // severity, and reversing another official's clearance, are single-official.
        val isDeEscalation = officialSeverity != null &&
            severityOrdinal(officialSeverity) < severityOrdinal(crowd.maxSeverity)
        val gated = isDeEscalation && contradicting >= DEESCALATION_COUNT
        val backingOfficials = officialEvents
            .filter { it.severity == officialSeverity }
            .map { it.authorId }
            .distinct()
            .size
        val officialInForce = latestOfficial != null &&
            (!gated || backingOfficials >= REQUIRED_OFFICIALS_TO_DEESCALATE)

        val resolution = if (officialInForce) {
            resolveOfficial(latestOfficial!!, weightBySeverity)
        } else {
            crowd
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
            officialSeverity = officialSeverity,
            officialAuthorName = latestOfficial?.authorName,
            officialAtMs = latestOfficial?.timestampMs,
            pendingSecondOfficial = latestOfficial != null && gated && !officialInForce,
            contradictingCount = contradicting,
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

        if (conflicted) return Resolution(resolvedSeverity, conflicted = true, bucket = "unverified", confidence = 0.0, maxSeverity = maxSeverity)

        // docs/03-architecture.md §5.3: "Unverified: confidence < 0.35, OR a single
        // report" — the OR matters, since a lone report's Wilson bound can round to
        // just above 0.35 depending on weight; a single observer must never read as
        // more than Unverified regardless.
        if (weighted.size <= 1) {
            return Resolution(
                resolvedSeverity,
                conflicted = false,
                bucket = "unverified",
                confidence = weightBySeverity[resolvedSeverity]?.let { wilsonLowerBound(it, 0.0) } ?: 0.0,
                maxSeverity = maxSeverity,
            )
        }

        val agree = weightBySeverity[resolvedSeverity] ?: 0.0
        val disagree = weightBySeverity.filterKeys { it != resolvedSeverity }.values.sum()
        val confidence = wilsonLowerBound(agree, disagree)
        val proximateAgreeing = weighted.count { it.severity == resolvedSeverity && it.proximate }
        val bucket = when {
            confidence >= 0.65 && proximateAgreeing >= 2 -> "confirmed"
            confidence >= 0.35 -> "likely"
            else -> "unverified"
        }
        return Resolution(resolvedSeverity, conflicted = false, bucket = bucket, confidence = confidence, maxSeverity = maxSeverity)
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
