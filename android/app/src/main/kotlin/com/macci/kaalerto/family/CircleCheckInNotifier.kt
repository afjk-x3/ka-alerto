package com.macci.kaalerto.family

import android.content.Context
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.notification.CircleCheckInNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Fires a local notification when a circle member checks in. Mirrors
 * `geofence/GeofenceNotifier.kt`'s shape exactly: the first Flow emission establishes a
 * baseline with no notifications (a fresh install, or a cold start with a backlog of
 * old check-ins, must not fire N notifications at once), and only a genuinely new
 * [TYPE_CHECKIN] event from someone in [effectiveCircle] counts. Pairing itself
 * (`circle_invite`) is deliberately silent — see the design spec's Notifications section.
 */
class CircleCheckInNotifier(private val context: Context) {
    fun start(scope: CoroutineScope) {
        val repository = EventRepository(KaAlertoDatabase.getInstance(context).eventDao())
        scope.launch {
            var knownIds: Set<String>? = null
            repository.observeAll().collect { events ->
                val currentIds = events.map { it.id }.toSet()
                val previous = knownIds
                if (previous != null) {
                    val myAuthorId = LocalIdentity.getOrCreate(context).authorId
                    val circleIds = effectiveCircle(CircleStore.get(context), events, myAuthorId)
                        .map { it.authorId }
                        .toSet()
                    events
                        .asSequence()
                        .filter { it.id !in previous && it.type == TYPE_CHECKIN && it.authorId in circleIds }
                        .forEach { event -> CircleCheckInNotification.notify(context, event) }
                }
                knownIds = currentIds
            }
        }
    }
}
