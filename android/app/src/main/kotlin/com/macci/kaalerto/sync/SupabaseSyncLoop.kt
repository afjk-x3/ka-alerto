package com.macci.kaalerto.sync

import android.content.Context
import android.util.Log
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.mesh.newlyAppeared
import com.macci.kaalerto.report.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** How many consecutive failed cycles before the header tells someone to turn Bluetooth on. */
private const val SLOW_THRESHOLD = 3

/**
 * A real, observed run of failures — not an OS bandwidth guess, which is routinely wrong
 * on Wi-Fi. `map/MapHeader.kt` reads [slow] to show "Mabagal ang koneksyon — buksan ang
 * Bluetooth" when it flips true.
 */
object SupabaseSyncState {
    private val _slow = MutableStateFlow(false)
    val slow: StateFlow<Boolean> = _slow.asStateFlow()
    fun setSlow(value: Boolean) { _slow.value = value }
}

/**
 * Phone-to-cloud sync, always on whenever [SupabaseConfig.isConfigured]: no address to
 * type, no server to find. Two ways an event leaves this device — the periodic
 * push-everything/pull-everything cycle, and [observeAndPushImmediately], which pushes a
 * just-filed report the moment it lands rather than waiting up to [SYNC_INTERVAL_MS].
 * [SupabaseSyncWorker] repeats the push when the app is closed.
 */
class SupabaseSyncLoop(private val context: Context) {
    fun start(scope: CoroutineScope) {
        if (!SupabaseConfig.isConfigured) return
        val repository = EventRepository(KaAlertoDatabase.getInstance(context).eventDao())
        observeAndPushImmediately(scope, repository)
        scope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                val pushed = runCatching { pushAll(repository) }
                    .onFailure { Log.w(TAG, "push cycle failed", it) }.isSuccess
                val pulled = runCatching { pullAll(repository) }
                    .onFailure { Log.w(TAG, "pull cycle failed", it) }.isSuccess
                if (pushed || pulled) {
                    consecutiveFailures = 0
                } else {
                    consecutiveFailures++
                }
                SupabaseSyncState.setSlow(consecutiveFailures >= SLOW_THRESHOLD)
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    /**
     * [newlyAppeared] (`mesh/MeshProtocol.kt`) is the exact "diff the event table's own
     * Flow, skip the first backlog emission" logic `MeshService.observeNewLocalEvents`
     * already uses for the same problem on the mesh side — reused rather than
     * reimplemented, since the decision ("what's new since last time") is identical.
     */
    private fun observeAndPushImmediately(scope: CoroutineScope, repository: EventRepository) {
        scope.launch {
            var knownIds: Set<String>? = null
            repository.observeAll().collect { events ->
                val fresh = newlyAppeared(knownIds, events)
                knownIds = events.map { it.id }.toSet()
                if (fresh.isEmpty()) return@collect
                runCatching {
                    pushEvents(fresh)
                    uploadPhotos(fresh)
                }.onFailure { Log.w(TAG, "immediate push failed", it) }
            }
        }
    }

    private suspend fun pushAll(repository: EventRepository) = pushEvents(repository.all())

    /** One push of everything local, for [SupabaseSyncWorker]. Throws on failure so the worker retries. */
    suspend fun pushNow() = pushAll(EventRepository(KaAlertoDatabase.getInstance(context).eventDao()))

    private suspend fun pushEvents(events: List<Event>) {
        val toPush = eventsToSync(events)
        if (toPush.isEmpty()) return
        postJson(buildEventsUrl(SupabaseConfig.URL), encodeEvents(toPush), upsert = true)
        uploadPhotos(toPush)
    }

    private suspend fun uploadPhotos(events: List<Event>) {
        eventsNeedingPhotoUpload(context, events).forEach { (_, hash) ->
            runCatching { uploadPhoto(hash) }
                .onFailure { Log.w(TAG, "photo upload failed for $hash", it) }
        }
    }

    private suspend fun uploadPhoto(hash: String) = withContext(Dispatchers.IO) {
        val bytes = PhotoStore.fileFor(context, hash).readBytes()
        val connection = URL("${SupabaseConfig.URL}/storage/v1/object/photos/$hash.jpg").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            connection.setRequestProperty("Content-Type", "image/jpeg")
            connection.setRequestProperty("x-upsert", "true")
            connection.outputStream.use { it.write(bytes) }
            connection.responseCode // a 409 (already uploaded) is fine; only I/O failure throws
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun pullAll(repository: EventRepository) {
        val url = buildPullUrl(SupabaseConfig.URL)
        val events = decodeEvents(getJson(url)) ?: error("GET $url did not decode to an event array")
        if (events.isNotEmpty()) repository.insert(stampSupabaseOrigin(eventsToSync(events)))
    }

    private suspend fun postJson(urlString: String, body: String, upsert: Boolean) = withContext(Dispatchers.IO) {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            connection.setRequestProperty("Content-Type", "application/json")
            if (upsert) connection.setRequestProperty("Prefer", "resolution=merge-duplicates")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code >= 400) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }
                error("POST $urlString failed: $code $err")
            }
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
            connection.setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            val code = connection.responseCode
            if (code >= 400) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }
                error("GET $urlString failed: $code $err")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val SYNC_INTERVAL_MS = 30_000L
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val TAG = "SupabaseSync"
    }
}

/**
 * Lazy fetch on demand — `detail/DetailSheet.kt` calls this when a viewer taps a photo
 * placeholder for a report whose bytes never reached this device. No proactive download
 * of every photo for every report: bandwidth a phone in a flood may not have to spare.
 */
suspend fun fetchPhotoFromSupabase(context: Context, hash: String): Boolean = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured) return@withContext false
    val connection = URL("${SupabaseConfig.URL}/storage/v1/object/photos/$hash.jpg").openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
        val code = connection.responseCode
        if (code != 200) {
            Log.w("SupabaseSync", "photo fetch for $hash failed: $code")
            return@withContext false
        }
        PhotoStore.storeDownloaded(context, hash, connection.inputStream.use { it.readBytes() })
    } catch (e: java.io.IOException) {
        Log.w("SupabaseSync", "photo fetch for $hash failed", e)
        false
    } finally {
        connection.disconnect()
    }
}
