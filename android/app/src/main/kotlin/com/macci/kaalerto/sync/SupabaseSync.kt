package com.macci.kaalerto.sync

import com.macci.kaalerto.data.Event
import com.macci.kaalerto.report.PhotoStore
import com.macci.kaalerto.report.decodeReportPhotoPayload
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * PostgREST's own upsert: an events row whose `id` already exists is merged, not
 * duplicated — same idempotent-on-id contract `server/src/server.js`'s
 * `INSERT OR IGNORE` has, so [SYNCED_TYPES]'s existing no-push-cursor reasoning
 * (`ServerSync.eventsToSync`'s doc comment) applies unchanged here.
 */
fun buildEventsUrl(baseUrl: String): String = "$baseUrl/rest/v1/events"

/**
 * No pull cursor and no location filter, on purpose — the same choice already made for
 * push (`ServerSync.eventsToSync`'s doc comment: re-sending costs one indexed lookup at
 * this app's volumes). A bbox filter here used to hardcode [com.macci.kaalerto.demo.DemoArea]
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

/** Stamps how this device learned about a pulled event — same convention as `ServerSync.stampServerOrigin`. */
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
