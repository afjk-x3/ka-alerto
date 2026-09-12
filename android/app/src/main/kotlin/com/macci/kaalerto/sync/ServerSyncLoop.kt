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
 * `sync/ServerSync.kt`). Runs as a coroutine loop from `KaAlertoApplication`, the same
 * shape as `family/CircleCheckInNotifier.kt`/`geofence/GeofenceNotifier.kt` — no
 * WorkManager, no foreground service, because an HTTP call is quick and does not need a
 * held-open connection the way `mesh/MeshService.kt`'s Nearby Connections session does.
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
            while (isActive) {
                val baseUrl = SyncPrefs.getServerUrl(context)
                if (!baseUrl.isNullOrBlank()) {
                    val pushed = runCatching { pushBatch(baseUrl, repository) }.isSuccess
                    val pulled = runCatching { pullDelta(baseUrl, repository) }.isSuccess
                    if (pushed || pulled) {
                        SyncPrefs.setLastSyncedAtMs(context, System.currentTimeMillis())
                    }
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private suspend fun pushBatch(baseUrl: String, repository: EventRepository) {
        val toPush = eventsToSync(repository.all())
        if (toPush.isEmpty()) return
        postJson(buildBatchUrl(baseUrl), encodeBatchRequest(toPush))
    }

    /** Loops on `hasMore` so a backlog drains in one cycle rather than trickling one page
     * every [SYNC_INTERVAL_MS]. */
    private suspend fun pullDelta(baseUrl: String, repository: EventRepository) {
        val bounds = DemoArea.bounds
        var cursor = SyncPrefs.getCursor(context)
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
            val page = decodePullResponse(getJson(url)) ?: return
            if (page.events.isNotEmpty()) {
                repository.insert(stampServerOrigin(page.events))
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
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }
}
