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
 * No pull cursor, on purpose — the same choice already made for push
 * (`ServerSync.eventsToSync`'s doc comment: re-sending costs one indexed lookup at this
 * app's volumes). A `seq`-style cursor needs a column Supabase's schema does not have and
 * this app does not need one to add: pulling the whole bbox every cycle and relying on
 * Room's own id-primary-key dedup on insert is the smaller diff for a barangay's worth of
 * events.
 */
fun buildPullUrl(baseUrl: String, minLon: Double, minLat: Double, maxLon: Double, maxLat: Double): String =
    "$baseUrl/rest/v1/events?lon=gte.$minLon&lon=lte.$maxLon&lat=gte.$minLat&lat=lte.$maxLat&select=*"

private val supabaseJson = Json { ignoreUnknownKeys = true }
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
