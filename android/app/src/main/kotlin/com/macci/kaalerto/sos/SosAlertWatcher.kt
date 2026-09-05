package com.macci.kaalerto.sos

import android.content.Context
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.geofence.HomeLocationStore
import com.macci.kaalerto.identity.LocalIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Raises the critical alert when somebody else's SOS lands in this device's store —
 * whether it arrived over the mesh, or (later) by SMS or server sync.
 *
 * Built the same way as [com.macci.kaalerto.geofence.GeofenceNotifier], and for the same
 * reason: it diffs the event table's own `Flow` rather than hooking every insert site,
 * so any transport that writes through [EventRepository] gets this for free.
 *
 * **The first emission is not simply skipped**, which is where this differs from the
 * geofence watcher. That one establishes a silent baseline so a fresh install does not
 * fire 19 notifications for seeded reports. Doing the same here would mean a phone that
 * rebooted mid-flood comes back up silent about a neighbour who called for help ninety
 * seconds ago — the app restarting is not evidence that the emergency ended. So on the
 * first emission it still alerts, but only for requests younger than
 * [FRESH_ON_STARTUP_MS]; anything older is treated as already-seen history.
 *
 * Three things it deliberately does not alert on:
 *
 *  - **My own request.** The requester already has a whole screen about it.
 *  - **A request that is already closed.** An SOS relayed in after the person was
 *    rescued is history, not an emergency — the fold decides that, not the arrival.
 *  - **The same request twice.** A `sos_amend` following its `sos` is more detail about
 *    a rescue already alerted, and BUILD_TASKS.md day 5's anti-fatigue reasoning applies
 *    with more force here, not less.
 */
class SosAlertWatcher(private val context: Context) {

    companion object {
        /**
         * How recent a request has to be to still be worth shouting about when the app
         * has only just started. Ten minutes: long enough to cover a reboot or a force
         * stop during an incident, short enough that opening the app hours later does
         * not replay a rescue that has long since resolved one way or the other.
         */
        const val FRESH_ON_STARTUP_MS = 10L * 60 * 1000
    }

    fun start(scope: CoroutineScope) {
        val repository = EventRepository(KaAlertoDatabase.getInstance(context).eventDao())
        val identity = LocalIdentity.getOrCreate(context)

        scope.launch {
            var alerted: Set<String>? = null
            repository.observeAll()
                .map { events -> SosReducer.all(events, identity.authorId) }
                .collect { snapshots ->
                    val now = System.currentTimeMillis()
                    val previous = alerted
                    val worthAlerting = snapshots.filter { snapshot ->
                        if (snapshot.isMine || !snapshot.isActive) return@filter false
                        if (previous == null) {
                            now - snapshot.startedAtMs <= FRESH_ON_STARTUP_MS
                        } else {
                            snapshot.sosId !in previous
                        }
                    }

                    if (worthAlerting.isNotEmpty()) {
                        val home = HomeLocationStore.get(context)
                        worthAlerting.forEach { snapshot ->
                            SosAlertNotifier.notify(context, snapshot, home?.lat, home?.lon)
                        }
                    }

                    // Baseline covers every request seen so far, closed ones included, so
                    // a request that closes and is later amended cannot re-alert.
                    alerted = snapshots.map { it.sosId }.toSet()
                }
        }
    }
}
