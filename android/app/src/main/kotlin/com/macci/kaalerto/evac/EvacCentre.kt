package com.macci.kaalerto.evac

import android.content.Context
import androidx.compose.runtime.Composable
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.haversineMeters
import com.macci.kaalerto.demo.DemoArea
import com.macci.kaalerto.i18n.tr
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
    /** "San Nicolas, Ilocos Norte". Absent in the bundled JSON; [loadEvacCentres] fills in the demo area's. */
    val municipality: String? = null,
    val barangay: String? = null,
    /** True for a shelter an official added (an `evac_centre` event), false for one bundled with the app. */
    val custom: Boolean = false,
)

@Serializable
private data class EvacFile(val centres: List<EvacCentre> = emptyList())

/** Three states, from EvacCentres-Normal.dc.html's own badges. */
enum class EvacStatus(val key: String, val fil: String, val en: String) {
    ACCEPTING("accepting", "Tumatanggap", "Accepting"),
    NEARLY_FULL("nearly_full", "Halos puno", "Nearly full"),
    FULL("full", "Puno na", "Full"),
    NOT_OPEN("not_open", "Hindi pa bukas", "Not open yet"),
    ;

    companion object {
        fun from(key: String?): EvacStatus = values().firstOrNull { it.key == key } ?: NOT_OPEN
    }
}

/** [EvacStatus.fil]/[EvacStatus.en] resolved by [com.macci.kaalerto.i18n.LocalAppLanguage]. */
@Composable
fun EvacStatus.label(): String = tr(fil, en)

/** What an official posts about a centre — BUILD_TASKS.md day 10's "tiny event". */
@Serializable
data class EvacPayload(
    @SerialName("centreId") val centreId: String,
    @SerialName("status") val status: String,
    /** Head count now inside. Null when the official only changed the status. */
    @SerialName("occupancy") val occupancy: Int? = null,
    /**
     * The updating official's municipality. [evacStates] ignores an update whose municipality
     * differs from the shelter's; null (older events) is accepted. Without signatures this is a
     * procedure, not a guarantee, like every other role check here.
     */
    @SerialName("municipality") val municipality: String? = null,
)

/** Shelter kinds an official can pick when adding one. */
val EVAC_KINDS = listOf("evacuation_centre", "school", "gym", "barangay_hall", "church", "other")

/**
 * An official adding, or removing, a shelter — the `evac_centre` event. Later events for the same
 * [centreId] replace earlier ones, but only when they come from the municipality that created it.
 */
@Serializable
data class EvacCentrePayload(
    @SerialName("centreId") val centreId: String,
    @SerialName("name") val name: String,
    @SerialName("kind") val kind: String = "other",
    @SerialName("lat") val lat: Double,
    @SerialName("lon") val lon: Double,
    @SerialName("municipality") val municipality: String,
    @SerialName("barangay") val barangay: String? = null,
    @SerialName("capacityEstimate") val capacityEstimate: Int? = null,
    /** A removed shelter is hidden. Only shelters an official added can be removed, never the bundled ones. */
    @SerialName("removed") val removed: Boolean = false,
)

fun EvacCentrePayload.encode(): String = evacJson.encodeToString(EvacCentrePayload.serializer(), this)

fun decodeEvacCentrePayload(raw: String?): EvacCentrePayload? =
    raw?.let { runCatching { evacJson.decodeFromString(EvacCentrePayload.serializer(), it) }.getOrNull() }

fun EvacPayload.encode(): String = evacJson.encodeToString(EvacPayload.serializer(), this)

fun decodeEvacPayload(raw: String?): EvacPayload? =
    raw?.let { runCatching { evacJson.decodeFromString(EvacPayload.serializer(), it) }.getOrNull() }

fun loadEvacCentres(context: Context): List<EvacCentre> = runCatching {
    val raw = context.assets.open(EVAC_ASSET).bufferedReader().use { it.readText() }
    evacJson.decodeFromString(EvacFile.serializer(), raw).centres.map {
        it.copy(municipality = it.municipality ?: DemoArea.MUNICIPALITY, barangay = it.barangay ?: DemoArea.BARANGAY_NAME)
    }
}.getOrElse { emptyList() }

/**
 * The bundled centres plus the shelters officials added: the latest `evac_centre` event per id wins,
 * ordered by time and then event id so two devices with the same events agree (NFR-4). A centre's
 * municipality is its creator's; a later event from another municipality is ignored, so one
 * municipality's officials cannot rewrite or remove another's shelter. A removed shelter disappears.
 * Events reusing a bundled id are ignored: the bundled four can be closed but not removed.
 */
fun resolveCentres(bundled: List<EvacCentre>, events: List<Event>): List<EvacCentre> {
    val bundledIds = bundled.mapTo(HashSet()) { it.id }
    val added = events.asSequence()
        .filter { it.type == TYPE_EVAC_CENTRE }
        .mapNotNull { event -> decodeEvacCentrePayload(event.payload)?.let { Triple(event.timestampMs, event.id, it) } }
        .filter { (_, _, p) -> p.centreId !in bundledIds && p.name.isNotBlank() && p.municipality.isNotBlank() }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .groupBy { it.third.centreId }
        .mapNotNull { (id, history) ->
            val owner = history.first().third.municipality
            val live = history.last { (_, _, p) -> sameMunicipality(p.municipality, owner) }.third
            if (live.removed) {
                null
            } else {
                EvacCentre(
                    id = id,
                    name = live.name.trim(),
                    lat = live.lat,
                    lon = live.lon,
                    kind = live.kind,
                    capacityEstimate = live.capacityEstimate,
                    municipality = owner.trim(),
                    barangay = live.barangay?.trim()?.ifBlank { null },
                    custom = true,
                )
            }
        }
    return bundled + added
}

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
    bundled: List<EvacCentre>,
    events: List<Event>,
    fromLat: Double?,
    fromLon: Double?,
): List<EvacState> {
    val centres = resolveCentres(bundled, events)
    val municipalityOf = centres.associate { it.id to it.municipality }
    val latestByCentre = events
        .asSequence()
        .filter { it.type == TYPE_EVAC_STATUS }
        .mapNotNull { event -> decodeEvacPayload(event.payload)?.let { event to it } }
        // A status counts only for a shelter that exists and, when the update says which municipality
        // it is from, only if that is the shelter's own.
        .filter { (_, payload) ->
            payload.centreId in municipalityOf &&
                (payload.municipality == null || sameMunicipality(payload.municipality, municipalityOf[payload.centreId]))
        }
        .sortedWith(compareBy({ it.first.timestampMs }, { it.first.id }))
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

/** Event type for an official adding (or removing) a shelter. */
const val TYPE_EVAC_CENTRE = "evac_centre"
