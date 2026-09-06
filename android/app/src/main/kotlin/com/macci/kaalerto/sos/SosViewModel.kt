package com.macci.kaalerto.sos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.identity.LocalIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EventRepository(KaAlertoDatabase.getInstance(application).eventDao())
    /**
     * Re-read on every use, not captured once.
     *
     * Day 9's `setRole` used to refresh this by hand; the 6 September role rebuild moved
     * role ownership to [com.macci.kaalerto.identity.RoleViewModel], and a cached copy
     * here would quietly keep authoring events as whatever this device was when the
     * ViewModel was constructed — an acknowledgement stamped `authorRole = "resident"`
     * from a phone that is now a responder. `authorId` never changes, so the flows below
     * are unaffected either way; it is the role and the display name that move.
     */
    private val identity: LocalIdentity.Identity
        get() = LocalIdentity.getOrCreate(getApplication())

    /**
     * Every SOS this device knows of, folded from the event log on each change — the
     * same recompute-don't-store approach as [com.macci.kaalerto.map.MapViewModel].
     */
    val snapshots: StateFlow<List<SosSnapshot>> = repository.observeAll()
        .map { events -> SosReducer.all(events, identity.authorId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everyone else's open requests — the responder queue. */
    val incoming: StateFlow<List<SosSnapshot>> = repository.observeAll()
        .map { events -> SosReducer.activeOthers(events, identity.authorId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The official queue: other people's open requests, grouped by proximity and ordered
     * with `QueueOfficial.dc.html`'s false-alarm demotion applied. Folded from the same
     * event stream as everything else, so an incoming mark from another official over
     * the mesh reorders this list with no extra plumbing.
     */
    val incidents: StateFlow<List<SosIncident>> = repository.observeAll()
        .map { events ->
            officialQueue(SosReducer.activeOthers(events, identity.authorId), foldTriage(events))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeMine: StateFlow<SosSnapshot?> = repository.observeAll()
        .map { events -> SosReducer.activeMine(events, identity.authorId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun snapshotOf(sosId: String): SosSnapshot? = snapshots.value.firstOrNull { it.sosId == sosId }

    /**
     * `QueueOfficial.dc.html`'s "Markahan: walang emergency", and its undo.
     *
     * Guarded on the *current* role rather than trusted from the screen: this is the one
     * action in the app that makes a future emergency harder to see, so an official who
     * has since been stood down must not still be able to author one. `foldTriage`
     * enforces the same rule on receipt — this stops a bad write leaving the mark
     * standing on the originating phone alone.
     */
    fun markFalseAlarm(incident: SosIncident, undo: Boolean) {
        val author = identity
        if (author.authorRole != LocalIdentity.ROLE_OFFICIAL) return
        viewModelScope.launch {
            repository.insert(
                falseAlarmEvent(
                    official = author,
                    request = incident.primary,
                    subjectAuthorId = incident.primary.authorId,
                    nowMs = System.currentTimeMillis(),
                    undo = undo,
                ),
            )
        }
    }

    /**
     * Writes the request and returns its id immediately. FR-4.3 and
     * `docs/03-architecture.md` §6.1 t+0.0: the local write is the *first* thing that
     * happens, before a fix is refined and before any screen asks for context.
     */
    fun raise(lat: Double, lon: Double, accuracyMeters: Float?, onRaised: (String) -> Unit) {
        val event = newSosEvent(identity, lat, lon, accuracyMeters, System.currentTimeMillis())
        viewModelScope.launch {
            repository.insert(event)
            onRaised(event.id)
        }
    }

    fun amend(sosId: String, context: SosContext) {
        val snapshot = snapshotOf(sosId) ?: return
        if (context.isEmpty) return
        viewModelScope.launch {
            repository.insert(
                sosAmendEvent(
                    sosId = sosId,
                    identity = identity,
                    lat = snapshot.lat,
                    lon = snapshot.lon,
                    context = context,
                    nowMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Day 9's acknowledgement. Writes a `sos_state` event exactly like any other, which
     * is the whole trick: it rides the same mesh the request arrived on, back to the
     * originator, with no server between them. Monotonicity is enforced in the fold, so
     * an ack overtaking a BEACONING in flight still lands correctly.
     */
    fun advance(sosId: String, state: SosState) = write(sosId, state)

    fun close(sosId: String, state: SosState) = write(sosId, state)

    private fun write(sosId: String, state: SosState) {
        val snapshot = snapshotOf(sosId) ?: return
        viewModelScope.launch {
            repository.insert(
                sosStateEvent(
                    sosId = sosId,
                    identity = identity,
                    lat = snapshot.lat,
                    lon = snapshot.lon,
                    state = state,
                    nowMs = System.currentTimeMillis(),
                ),
            )
        }
    }
}
