package com.macci.kaalerto.sos

import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.haversineMeters
import com.macci.kaalerto.identity.LocalIdentity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** An official marks a request as not a real emergency. Attributed, and undoable. */
const val TYPE_SOS_FALSE_ALARM = "sos_false_alarm"

/** Any official can lift another's mark — a wrong call must not be permanent. */
const val TYPE_SOS_FALSE_ALARM_UNDO = "sos_false_alarm_undo"

val SOS_TRIAGE_TYPES = setOf(TYPE_SOS_FALSE_ALARM, TYPE_SOS_FALSE_ALARM_UNDO)

/**
 * How close two open requests must be before the official queue treats them as one
 * incident.
 *
 * `QueueOfficial.dc.html` words this as "3 ulat mula sa iisang bahay — isang insidente".
 * **This code does not claim the house.** Fixes observed on device this session ran from
 * ±5 m to ±100 m, and at ±100 m a cluster is a block, not a building — so the screen says
 * the requests are near each other and shows the accuracy, letting the official make the
 * judgement the phone cannot.
 *
 * 40 m is chosen to be smaller than a typical accuracy circle rather than larger: the
 * failure that matters is merging two genuinely separate households into one row and
 * sending half the rescue, so this errs toward leaving them apart.
 */
const val SAME_INCIDENT_RADIUS_M = 40.0

private val triageJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class FalseAlarmPayload(
    val sosId: String,
    /** Whose future requests this bears on. Survives mesh redaction; the name does not. */
    val subjectAuthorId: String,
)

fun FalseAlarmPayload.encode(): String =
    triageJson.encodeToString(FalseAlarmPayload.serializer(), this)

fun decodeFalseAlarmPayload(raw: String?): FalseAlarmPayload? =
    raw?.let { runCatching { triageJson.decodeFromString(FalseAlarmPayload.serializer(), it) }.getOrNull() }

/**
 * Several open requests the official queue shows as one row.
 *
 * [others] is never dropped — it is nested under [primary] and can be opened. A queue
 * that *hid* a request because something nearby looked similar would be the one failure
 * this screen cannot be allowed: grouping is a reading aid, not a filter.
 */
data class SosIncident(
    val primary: SosSnapshot,
    val others: List<SosSnapshot>,
    val falseAlarm: FalseAlarmMark?,
    /** Prior marks against this requester's device. Demotes, never hides. */
    val priorFalseAlarms: Int,
) {
    val all: List<SosSnapshot> get() = listOf(primary) + others
    val size: Int get() = 1 + others.size

    /**
     * The buckets as reported, never added up. `SosContext.people` is "2–4", not 3, on
     * purpose — nobody counts heads in a flood — so summing them would invent a total
     * precision the requesters were deliberately never asked for.
     */
    val peopleBuckets: List<String> get() = all.mapNotNull { it.context.people }

    val worstAccuracyM: Float? get() = all.mapNotNull { it.accuracyMeters }.maxOrNull()
}

data class FalseAlarmMark(val byName: String, val atMs: Long)

/**
 * Which requests currently carry an official "walang emergency" mark, and how many marks
 * stand against each requester's device.
 *
 * Both are folds, and both are last-writer-wins per request on `(timestamp, id)` so that
 * a mark and an undo arriving in either order over the mesh settle the same way on every
 * phone. A mark authored by a non-official is ignored — same rule as day 10's rulings.
 */
data class TriageState(
    val marks: Map<String, FalseAlarmMark> = emptyMap(),
    val priorsByAuthor: Map<String, Int> = emptyMap(),
)

fun foldTriage(events: List<Event>): TriageState {
    val decisions = events
        .filter { it.type in SOS_TRIAGE_TYPES && it.authorRole == LocalIdentity.ROLE_OFFICIAL }
        .mapNotNull { event -> decodeFalseAlarmPayload(event.payload)?.let { event to it } }
        .sortedWith(compareBy({ it.first.timestampMs }, { it.first.id }))

    val latest = LinkedHashMap<String, Pair<Event, FalseAlarmPayload>>()
    for (decision in decisions) latest[decision.second.sosId] = decision

    val marks = latest.values
        .filter { (event, _) -> event.type == TYPE_SOS_FALSE_ALARM }
        .associate { (event, payload) ->
            payload.sosId to FalseAlarmMark(byName = event.authorName, atMs = event.timestampMs)
        }

    val priors = latest.values
        .filter { (event, _) -> event.type == TYPE_SOS_FALSE_ALARM }
        .groupingBy { it.second.subjectAuthorId }
        .eachCount()

    return TriageState(marks = marks, priorsByAuthor = priors)
}

/**
 * The official queue's ordering, and the one place the artboard's
 * "bababa ang pagkakasunod ng susunod na request ng device na ito" is implemented.
 *
 * Read the sort carefully, because what it deliberately does *not* do is the point. A
 * marked request and a request from a previously-marked device both sink; neither is
 * removed, filtered or collapsed away, and the row says why it sank. Every other
 * mechanism in this app errs loud — the reducer treats a conflict as impassable, an
 * unverified report still renders — and this is the only one that can make an emergency
 * quieter, so it is bounded to sort order and nothing else.
 */
fun officialQueue(
    requests: List<SosSnapshot>,
    triage: TriageState,
    radiusMeters: Double = SAME_INCIDENT_RADIUS_M,
): List<SosIncident> {
    val open = requests.filter { it.isActive }
    val incidents = groupNearby(open, radiusMeters).map { group ->
        val primary = group.first()
        SosIncident(
            primary = primary,
            others = group.drop(1),
            falseAlarm = triage.marks[primary.sosId],
            priorFalseAlarms = triage.priorsByAuthor[primary.authorId] ?: 0,
        )
    }
    return incidents.sortedWith(
        compareBy(
            { it.falseAlarm != null },            // marked sinks below everything unmarked
            { it.priorFalseAlarms },              // then by how often this device cried wolf
            { -it.primary.startedAtMs },          // then newest first, as before
        ),
    )
}

/**
 * Clusters by proximity to the group's *first* member rather than by transitive chaining.
 * Chaining would let a line of requests 39 m apart merge a whole street into one
 * incident, which is exactly the over-merge this radius is chosen to avoid.
 */
internal fun groupNearby(open: List<SosSnapshot>, radiusMeters: Double): List<List<SosSnapshot>> {
    val remaining = open.sortedByDescending { it.startedAtMs }.toMutableList()
    val groups = mutableListOf<List<SosSnapshot>>()
    while (remaining.isNotEmpty()) {
        val seed = remaining.removeAt(0)
        val near = remaining.filter {
            haversineMeters(seed.lat, seed.lon, it.lat, it.lon) <= radiusMeters
        }
        remaining.removeAll(near)
        groups += listOf(seed) + near
    }
    return groups
}

fun falseAlarmEvent(
    official: LocalIdentity.Identity,
    request: SosSnapshot,
    subjectAuthorId: String,
    nowMs: Long,
    undo: Boolean,
): Event = Event(
    id = "triage-${UUID.randomUUID()}",
    type = if (undo) TYPE_SOS_FALSE_ALARM_UNDO else TYPE_SOS_FALSE_ALARM,
    lat = 0.0,
    lon = 0.0,
    // Null for the same reason every sos* event sets it null — see SosEvents.kt.
    featureRef = null,
    severity = null,
    waterLevel = null,
    authorId = official.authorId,
    authorName = official.authorName,
    authorRole = official.authorRole,
    timestampMs = nowMs,
    expiresAt = nowMs + SOS_TTL_MS,
    origin = "local",
    hopCount = 0,
    note = null,
    payload = FalseAlarmPayload(sosId = request.sosId, subjectAuthorId = subjectAuthorId).encode(),
)
