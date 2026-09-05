package com.macci.kaalerto.sos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.identity.LocalIdentity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EventRepository(KaAlertoDatabase.getInstance(application).eventDao())
    private val identity = LocalIdentity.getOrCreate(application)

    /**
     * Every SOS this device knows of, folded from the event log on each change — the
     * same recompute-don't-store approach as [com.macci.kaalerto.map.MapViewModel].
     */
    val snapshots: StateFlow<List<SosSnapshot>> = repository.observeAll()
        .map { events -> SosReducer.all(events, identity.authorId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeMine: StateFlow<SosSnapshot?> = repository.observeAll()
        .map { events -> SosReducer.activeMine(events, identity.authorId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun snapshotOf(sosId: String): SosSnapshot? = snapshots.value.firstOrNull { it.sosId == sosId }

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

    fun close(sosId: String, state: SosState) {
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
