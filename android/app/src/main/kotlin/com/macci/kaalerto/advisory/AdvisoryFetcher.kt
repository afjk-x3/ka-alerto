package com.macci.kaalerto.advisory

import android.content.Context
import android.util.Log
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.demo.DemoArea
import com.macci.kaalerto.identity.LocalIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** The resident's home province ("Pangasinan"), from "Mapandan, Pangasinan"; the demo area's before registration. */
fun homeProvince(context: Context): String =
    LocalIdentity.homeMunicipality(context).substringAfter(", ", "").ifBlank { DemoArea.MUNICIPALITY.substringAfter(", ") }

/**
 * Reads PAGASA's feed every [INTERVAL_MS] while the app runs and stores each new alert
 * covering the home province (see Advisories.kt). No connection simply means no fetch —
 * phones without signal get the alerts over the relay from one that had it. Paused in
 * Survival mode.
 */
class AdvisoryFetcher(private val context: Context) {
    // Alerts already fetched that do not cover this province, so they are not fetched again.
    private val skipped = mutableSetOf<String>()

    fun start(scope: CoroutineScope) {
        val repository = EventRepository(KaAlertoDatabase.getInstance(context).eventDao())
        scope.launch {
            while (isActive) {
                if (!com.macci.kaalerto.ui.SurvivalState.active.value) {
                    runCatching { refresh(repository) }.onFailure { Log.w(TAG, "advisory fetch failed", it) }
                }
                delay(INTERVAL_MS)
            }
        }
    }

    private suspend fun refresh(repository: EventRepository) {
        val province = homeProvince(context)
        val known = repository.all().filter { it.type == TYPE_ADVISORY }.map { it.id }.toSet()
        val fresh = parseFeed(get(PAGASA_FEED))
            .filter { "pagasa-${it.capId}" !in known && it.capId !in skipped }
            .take(MAX_PER_RUN)
        for (entry in fresh) {
            val payload = parseCap(get(entry.capUrl), link = entry.capUrl)
            // A Cancel is kept when it names this province or cancels an alert already held.
            val relevant = payload != null &&
                (payload.covers(province) || payload.references.any { "pagasa-$it" in known })
            if (!relevant) {
                skipped += entry.capId
                continue
            }
            advisoryEvent(payload!!)?.let { repository.insert(it) }
        }
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "KaAlerto/1.0 (community flood map; relays PAGASA alerts verbatim)")
            if (connection.responseCode !in 200..299) error("GET $url: ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "AdvisoryFetcher"
        const val INTERVAL_MS = 10L * 60 * 1000
        // The feed holds ~50 entries; the first run reads them all once, later runs only new ones.
        const val MAX_PER_RUN = 60
    }
}
