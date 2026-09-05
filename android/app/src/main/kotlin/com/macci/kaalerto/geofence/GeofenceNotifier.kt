package com.macci.kaalerto.geofence

import android.content.Context
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.data.haversineMeters
import com.macci.kaalerto.notification.FloodNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * "Geofence check on every event insert (entirely local)" — BUILD_TASKS.md day 5.
 *
 * This sees every event inserted while the app process is alive — reports authored
 * locally, and, since day 6-7, arrivals over the mesh: mesh/MeshService.kt inserts
 * through the same repository, so a neighbour's report relayed into the home radius
 * fires a notification with no extra wiring here. SMS (day 12) and server sync
 * (day 13) will land the same way. It works
 * by diffing the event table's own Flow rather than hooking every insert call site
 * (report submit, confirm/dispute submit, seed load): the first emission establishes
 * the baseline with no notifications (so a fresh install doesn't fire 19 seed
 * notifications), and only IDs that are genuinely new after that are checked.
 *
 * Only fresh `flood_report` events are notification-worthy — a confirm or dispute is
 * a confidence update, not new flooding, and firing on every one of those would be
 * exactly the alert fatigue docs/03-architecture.md's anti-fatigue rules warn against.
 */
class GeofenceNotifier(private val context: Context) {
    fun start(scope: CoroutineScope) {
        val repository = EventRepository(KaAlertoDatabase.getInstance(context).eventDao())
        scope.launch {
            var knownIds: Set<String>? = null
            repository.observeAll().collect { events ->
                val currentIds = events.map { it.id }.toSet()
                val previous = knownIds
                if (previous != null) {
                    val home = HomeLocationStore.get(context)
                    if (home != null) {
                        events
                            .asSequence()
                            .filter { it.id !in previous && it.type == "flood_report" && it.origin != "seed" }
                            .forEach { event ->
                                val distance = haversineMeters(home.lat, home.lon, event.lat, event.lon)
                                if (distance <= home.radiusMeters) {
                                    FloodNotifier.notify(context, event, distance)
                                }
                            }
                    }
                }
                knownIds = currentIds
            }
        }
    }
}
