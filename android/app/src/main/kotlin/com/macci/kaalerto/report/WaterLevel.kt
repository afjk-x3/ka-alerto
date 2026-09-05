package com.macci.kaalerto.report

/** Body-referenced or vehicle-referenced depth scale (docs/02-prd.md §6, FR-2.1). */
enum class ReportMode { BODY, VEHICLE }

/** One selectable depth. `severity` is what FR-2.1 calls deriving severity "automatically" from depth. */
data class WaterLevelOption(
    val id: String,
    val fil: String,
    val en: String,
    val severity: String,
)

/** Severity-tier copy, shared by every scale so an override always shows the canonical text for the tier it lands on, not whichever option happened to produce it. */
private val SEVERITY_TEXT = mapOf(
    "S1" to ("Madaanan, mag-ingat" to "Passable with caution"),
    "S2" to ("Hindi madaanan ng sasakyan" to "Impassable for cars"),
    "S3" to ("Hindi madaanan" to "Impassable for all"),
)

fun severityTextFor(severity: String): Pair<String, String> = SEVERITY_TEXT[severity] ?: ("Hindi tiyak" to "Unknown")

/**
 * Exact FIL/EN copy and severity mapping lifted from
 * design/artboards/Report-Normal.dc.html's own `levels` data — that artboard is the
 * canonical source for this scale, not something reinvented here. Waist and chest both
 * land on S3: the artboard itself maps waist to S3 ("Hindi madaanan" / "Impassable for
 * all"), and docs/03-architecture.md's worked Rule-C example independently pairs
 * "waist-deep" with S3 too, so this isn't a rounding choice — it's what the design
 * already specifies.
 */
val BODY_LEVELS = listOf(
    WaterLevelOption("ankle", "Bukong-bukong", "Ankle", "S1"),
    WaterLevelOption("knee", "Tuhod", "Knee", "S2"),
    WaterLevelOption("waist", "Baywang", "Waist", "S3"),
    WaterLevelOption("chest", "Dibdib", "Chest", "S3"),
)

/**
 * The vehicle-referenced alternative named in BUILD_TASKS.md day 3 ("+ vehicle icons")
 * and docs/03-architecture.md §103 ("truck can pass / car can pass / motorcycle only /
 * nothing can pass"). No artboard fleshes this tab out with its own data (Report-Normal
 * only wires up the body scale), so the severity text per tier is copied verbatim from
 * docs/03-architecture.md's S1-S3 definitions rather than invented: S2's own definition
 * ("Trucks/motorcycles may pass; cars must not") explicitly covers both the truck and
 * motorcycle-only tiers, so both land on S2 — the same "two depths share a tier"
 * pattern the body scale above already uses for waist/chest.
 */
val VEHICLE_LEVELS = listOf(
    WaterLevelOption("car", "Kotse", "Car", "S1"),
    WaterLevelOption("truck", "Trak", "Truck only", "S2"),
    WaterLevelOption("motorcycle", "Motorsiklo", "Motorcycle only", "S2"),
    WaterLevelOption("none", "Wala", "Nothing can pass", "S3"),
)

fun levelsFor(mode: ReportMode): List<WaterLevelOption> = when (mode) {
    ReportMode.BODY -> BODY_LEVELS
    ReportMode.VEHICLE -> VEHICLE_LEVELS
}

/** Severity-dependent TTL in minutes — same table as the seed fixtures use. */
fun ttlMinutesFor(severity: String): Long = when (severity) {
    "S1" -> 120L
    "S2" -> 240L
    "S3" -> 360L
    else -> 60L
}
