package com.macci.kaalerto.sync

import android.content.Context
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.demo.DemoArea
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The second of this app's three transports (`docs/03-architecture.md` §2): a plain-HTTP
 * client for the already-built `server/`, scoped to [SYNCED_TYPES] only (see
 * `sync/ServerSync.kt`) on *both* push and pull — the server has no write auth either, so
 * an unfiltered pull would let anyone who can reach it inject `sos*`/`role_*` events into
 * every syncing device, not just read them back. Runs as a coroutine loop from
 * `KaAlertoApplication`, the same shape as
 * `family/CircleCheckInNotifier.kt`/`geofence/GeofenceNotifier.kt` — no WorkManager, no
 * foreground service, because an HTTP call is quick and does not need a held-open
 * connection the way `mesh/MeshService.kt`'s Nearby Connections session does.
 *
 * A blank/unset server URL (`SyncPrefs.getServerUrl` returning null) makes every cycle a
 * no-op — a device with no server nearby behaves exactly as it does today, the same
 * every-transport-is-optional property `AndroidManifest.xml`'s own `uses-feature` block
 * already states as a project-wide rule.
 */
class ServerSyncLoop(private val context: Context) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            val repository = EventRepository(KaAlertoDatabase.getInstance(context).eventDao())
            var consecutiveFailures = 0
            while (isActive) {
                val baseUrl = SyncPrefs.getServerUrl(context)
                if (!baseUrl.isNullOrBlank()) {
                    val pushed = runCatching { pushBatch(baseUrl, repository) }.isSuccess
                    val pulled = runCatching { pullDelta(baseUrl, repository) }.isSuccess
                    if (pushed || pulled) {
                        SyncPrefs.setLastSyncedAtMs(context, System.currentTimeMillis())
                        consecutiveFailures = 0
                    } else {
                        consecutiveFailures++
                    }
                }
                delay(
                    nextSyncDelayMs(
                        consecutiveFailures = consecutiveFailures,
                        normalIntervalMs = SYNC_INTERVAL_MS,
                        backedOffIntervalMs = BACKED_OFF_INTERVAL_MS,
                        failureThreshold = BACKOFF_FAILURE_THRESHOLD,
                    ),
                )
            }
        }
    }

    /** Splits into chunks under the server's own `MAX_BATCH_SIZE` (`server/src/server.js`)
     * so a large qualifying local event count never sends one oversized, permanently-413
     * request — see `ServerSync.chunkForPush`. A failing chunk throws and fails the whole
     * cycle via the caller's `runCatching`, same as any other push failure; the next cycle
     * retries the entire filtered set unchanged, matching this feature's existing
     * no-push-cursor design. */
    private suspend fun pushBatch(baseUrl: String, repository: EventRepository) {
        val toPush = eventsToSync(repository.all())
        if (toPush.isEmpty()) return
        val url = buildBatchUrl(baseUrl)
        chunkForPush(toPush).forEach { chunk ->
            postJson(url, encodeBatchRequest(chunk))
        }
    }

    /**
     * Loops on `hasMore` so a backlog drains in one cycle rather than trickling one page
     * every [SYNC_INTERVAL_MS]. Before that loop, validates the locally-stored cursor
     * against the server's own current cursor (`GET /health`) and resets to 0 if the local
     * cursor is higher than anything the server could have issued — see
     * `ServerSync.cursorIsStale`'s doc comment for why that situation is otherwise a
     * permanently dead pull path that reports as healthy. A failed health check (network
     * error, bad response) is swallowed here rather than failing the cycle — it means "no
     * answer to validate against this time," not "the cursor is definitely stale."
     *
     * Every pulled event is filtered through [eventsToSync] before insertion, the same
     * filter `pushBatch` applies going the other way — the server accepts writes from
     * anyone (ground rule 4, no auth), so an unfiltered pull would let `sos`/`role_*`
     * events injected by any device that can reach the server land on every other synced
     * device, not just ones physically nearby the way the mesh requires.
     */
    private suspend fun pullDelta(baseUrl: String, repository: EventRepository) {
        val bounds = DemoArea.bounds
        var cursor = SyncPrefs.getCursor(context)
        val serverCursor = runCatching { decodeHealthCursor(getJson(buildHealthUrl(baseUrl))) }.getOrNull()
        if (serverCursor != null && cursorIsStale(cursor, serverCursor)) {
            cursor = 0L
            SyncPrefs.setCursor(context, cursor)
        }
        var hasMore = true
        while (hasMore) {
            val url = buildPullUrl(
                baseUrl,
                minLon = bounds.longitudeWest,
                minLat = bounds.latitudeSouth,
                maxLon = bounds.longitudeEast,
                maxLat = bounds.latitudeNorth,
                since = cursor,
            )
            val page = decodePullResponse(getJson(url))
                ?: error("GET $url did not decode to the expected pull-response shape")
            if (page.events.isNotEmpty()) {
                repository.insert(stampServerOrigin(eventsToSync(page.events)))
            }
            cursor = page.nextCursor
            SyncPrefs.setCursor(context, cursor)
            hasMore = page.hasMore
        }
    }

    private suspend fun postJson(urlString: String, body: String) = withContext(Dispatchers.IO) {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode < 400) { "POST $urlString failed: ${connection.responseCode}" }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun getJson(urlString: String): String = withContext(Dispatchers.IO) {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            check(connection.responseCode < 400) { "GET $urlString failed: ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** A starting guess, easy to tune once this is running against a real server on
         * a real network — no reason to treat it as fixed. */
        const val SYNC_INTERVAL_MS = 30_000L

        /** The interval once [BACKOFF_FAILURE_THRESHOLD] consecutive cycles have produced
         * no successful push or pull — see `ServerSync.nextSyncDelayMs`. */
        const val BACKED_OFF_INTERVAL_MS = 5 * 60_000L
        const val BACKOFF_FAILURE_THRESHOLD = 3

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }
}
