package com.macci.kaalerto.evac

import android.content.Context
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.haversineMeters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val EVAC_ASSET = "evacuation_centres.json"
private val evacJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * One centre as it exists in `assets/evacuation_centres.json` — static, shipped with the
 * app, never fetched. Names and coordinates are real and OSM-confirmed;
 * [capacityEstimate] is **not**, and [capacityEstimateSource] says so in the fixture
 * itself. The UI repeats that rather than rendering the number as fact.
 */
@Serializable
data class EvacCentre(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val kind: String,
    val capacityEstimate: Int? = null,
    val capacityEstimateSource: String? = null,
)

@Serializable
private data class EvacFile(val centres: List<EvacCentre> = emptyList())

/** Three states, from EvacCentres-Normal.dc.html's own badges. */
enum class EvacStatus(val key: String, val fil: String) {
    ACCEPTING("accepting", "Tumatanggap"),
    NEARLY_FULL("nearly_full", "Halos puno"),
    NOT_OPEN("not_open", "Hindi pa bukas"),
    ;

    companion object {
        fun from(key: String?): EvacStatus = values().firstOrNull { it.key == key } ?: NOT_OPEN
    }
}

/** What an official posts about a centre — BUILD_TASKS.md day 10's "tiny event". */
@Serializable
data class EvacPayload(
    @SerialName("centreId") val centreId: String,
    @SerialName("status") val status: String,
    /** Head count now inside. Null when the official only changed the status. */
    @SerialName("occupancy") val occupancy: Int? = null,
)

fun EvacPayload.encode(): String = evacJson.encodeToString(EvacPayload.serializer(), this)

fun decodeEvacPayload(raw: String?): EvacPayload? =
    raw?.let { runCatching { evacJson.decodeFromString(EvacPayload.serializer(), it) }.getOrNull() }

fun loadEvacCentres(context: Context): List<EvacCentre> = runCatching {
    val raw = context.assets.open(EVAC_ASSET).bufferedReader().use { it.readText() }
    evacJson.decodeFromString(EvacFile.serializer(), raw).centres
}.getOrElse { emptyList() }

/**
 * A centre plus whatever an official has most recently said about it.
 *
 * [status] defaults to [EvacStatus.NOT_OPEN] on purpose: a centre existing in the
 * fixture is a building that could be opened, not one that is open. Showing it as
 * "Tumatanggap" before any official said so would send people to a locked school.
 */
data class EvacState(
    val centre: EvacCentre,
    val status: EvacStatus,
    val occupancy: Int?,
    val updatedAtMs: Long?,
    val updatedByName: String?,
    val distanceMeters: Double?,
) {
    /** Null unless both an occupancy and a capacity estimate exist. */
    val occupancyFraction: Float?
        get() {
            val capacity = centre.capacityEstimate ?: return null
            val filled = occupancy ?: return null
            if (capacity <= 0) return null
            return (filled.toFloat() / capacity).coerceIn(0f, 1f)
        }
}

/**
 * Folds the event log onto the static centre list — the same recompute-don't-store
 * approach as every other reducer here, so a status relayed in over the mesh needs no
 * extra plumbing to show up.
 */
fun evacStates(
    centres: List<EvacCentre>,
    events: List<Event>,
    fromLat: Double?,
    fromLon: Double?,
): List<EvacState> {
    val latestByCentre = events
        .asSequence()
        .filter { it.type == TYPE_EVAC_STATUS }
        .mapNotNull { event -> decodeEvacPayload(event.payload)?.let { event to it } }
        .sortedBy { (event, _) -> event.timestampMs }
        .associateBy { (_, payload) -> payload.centreId }

    return centres
        .map { centre ->
            val update = latestByCentre[centre.id]
            EvacState(
                centre = centre,
                status = EvacStatus.from(update?.second?.status),
                occupancy = update?.second?.occupancy,
                updatedAtMs = update?.first?.timestampMs,
                updatedByName = update?.first?.authorName,
                distanceMeters = if (fromLat != null && fromLon != null) {
                    haversineMeters(fromLat, fromLon, centre.lat, centre.lon)
                } else {
                    null
                },
            )
        }
        // "Pinakamalapit muna" (EvacCentres-Normal.dc.html). Centres with no known
        // distance sort last rather than pretending to be nearest.
        .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
}

/** Event type for an official's centre update. */
const val TYPE_EVAC_STATUS = "evac_status"
