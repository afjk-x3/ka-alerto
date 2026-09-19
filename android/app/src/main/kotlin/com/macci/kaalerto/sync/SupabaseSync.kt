package com.macci.kaalerto.sync

import com.macci.kaalerto.data.Event
import com.macci.kaalerto.sos.TYPE_SOS
import com.macci.kaalerto.sos.TYPE_SOS_AMEND
import com.macci.kaalerto.sos.TYPE_SOS_STATE
import com.macci.kaalerto.sos.redactSosOnEgress
import com.macci.kaalerto.report.PhotoStore
import com.macci.kaalerto.report.decodeReportPhotoPayload
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Only these event types leave the device. `family_checkin`, `circle_invite` and `role_*`
 * stay mesh-only: Supabase has no access control (the anon key ships in the APK), so
 * anything posted is readable by anyone who extracts it. SOS is included, but always
 * redacted first — see [eventsToSync].
 */
val SYNCED_TYPES = setOf("flood_report", "confirm", "dispute", "official_status", TYPE_SOS, TYPE_SOS_AMEND, TYPE_SOS_STATE)

/**
 * Every locally-held event worth pushing — mesh-received ones included, not just
 * self-authored, which is what makes carry-forward work: a device that relayed someone
 * else's report with no connectivity must still upload it the next time it is online.
 * No push-side cursor, on purpose: a timestamp cursor would skip a relayed event whose own
 * `timestampMs` is old, and Supabase's upsert on the id makes re-sending free.
 *
 * Bundled sample reports (origin "seed") are never pushed: they are demo fixtures.
 * [redactSosOnEgress] is a no-op for every non-SOS type, so it maps over the whole list.
 */
fun eventsToSync(all: List<Event>): List<Event> =
    all.filter { it.type in SYNCED_TYPES && it.origin != "seed" }.map(::redactSosOnEgress)

/**
 * PostgREST's own upsert: an events row whose `id` already exists is merged, not
 * duplicated, so re-sending is free and [eventsToSync] needs no push cursor.
 */
fun buildEventsUrl(baseUrl: String): String = "$baseUrl/rest/v1/events"

/**
 * No pull cursor and no location filter, on purpose — the same choice already made for
 * push ([eventsToSync]: re-sending is free at this app's volumes). A bbox filter here used to hardcode [com.macci.kaalerto.demo.DemoArea]
 * (removed 18 Sep 2026): that silently dropped every report filed from a real GPS location
 * outside the frozen demo box, since push has no such filter but pull did — push a report
 * from real life and no other device would ever pull it back. Pulling everything and
 * relying on Room's own id-primary-key dedup on insert is the smaller diff for a
 * barangay's worth of events.
 */
fun buildPullUrl(baseUrl: String): String = "$baseUrl/rest/v1/events?select=*"

/**
 * `encodeDefaults = true`: PostgREST's batch insert rejects an array whose objects don't
 * all have the same keys (`PGRST102`). [Event.disputeReason] and [Event.payload] default to
 * null, so kotlinx.serialization's own default (omit a field at its default value) makes
 * two events serialize to different key sets — always encode every field so every row matches.
 */
private val supabaseJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val eventListSerializer = ListSerializer(Event.serializer())

/** PostgREST wants a bare JSON array of rows — `Event` is already the wire format. */
fun encodeEvents(events: List<Event>): String = supabaseJson.encodeToString(eventListSerializer, events)

fun decodeEvents(json: String): List<Event>? =
    runCatching { supabaseJson.decodeFromString(eventListSerializer, json) }.getOrNull()

/** Stamps how this device learned about a pulled event — the counterpart of `origin = "mesh"` for relayed events. */
fun stampSupabaseOrigin(events: List<Event>): List<Event> = events.map { it.copy(origin = "server") }

/**
 * Which of [events] are worth uploading a photo for: qualifies for sync
 * ([SYNCED_TYPES]), carries a photo hash, and this device actually has the bytes — a
 * mesh-received report's photo hash with no local file has nothing to upload.
 */
fun eventsNeedingPhotoUpload(context: android.content.Context, events: List<Event>): List<Pair<Event, String>> =
    eventsToSync(events).mapNotNull { event ->
        decodeReportPhotoPayload(event.payload)?.photoHash
            ?.takeIf { PhotoStore.exists(context, it) }
            ?.let { event to it }
    }

/** Consecutive failed cycles before the loop slows down. Also what the "slow connection" banner waits for. */
const val SLOW_THRESHOLD = 3

/**
 * 30 s while sync works, 2 min once [SLOW_THRESHOLD] cycles in a row have failed. Offline the
 * attempts fail fast, but there is nothing to gain from making one every 30 s for hours.
 * New reports still push immediately (observeAndPushImmediately), and the WorkManager job
 * plus the first cycle after a reconnect pick up the rest, so the slower tier only delays
 * pulling other people's reports.
 */
fun nextSyncDelayMs(consecutiveFailures: Int): Long = if (consecutiveFailures >= SLOW_THRESHOLD) 120_000L else 30_000L

/**
 * True for an event the cloud has probably never received: a type that syncs, not a sample,
 * not something we pulled from the cloud ourselves, and no full push has succeeded since it
 * expired. Once one full push succeeds after expiry, every event we then held was in it.
 * ([com.macci.kaalerto.data.EventRepository.deleteExpired] keeps these past the 24 h grace.)
 */
fun isAwaitingUpload(event: Event, lastFullPushOkMs: Long): Boolean =
    event.type in SYNCED_TYPES && event.origin != "seed" && event.origin != "server" && lastFullPushOkMs < event.expiresAt
