package com.macci.kaalerto.sos

import android.content.Context
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.mesh.MeshState
import com.macci.kaalerto.mesh.MeshStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Advances the SOS state machine from the signals this build can actually observe, by
 * appending `sos_state` events (see [SosState] for why transitions are events).
 *
 * `docs/03-architecture.md` §6.1: all channels are attempted concurrently, never in
 * sequence. There is only one real channel here — the day 6-7 mesh — so "concurrently"
 * is not yet a meaningful claim, and this makes no attempt to look like it is.
 *
 * Exactly two transitions are driven, and both are things that genuinely happened:
 *
 *  - **QUEUED → BEACONING** the moment the mesh radio is live. The request is already
 *    a row in the table peers reconcile against, so it is being offered outward.
 *  - **→ UNREACHABLE** at [UNREACHABLE_AFTER_MS], if nothing has produced a relay or a
 *    delivery. This is §6.1's t+30 s row, and it is what raises the rescue card.
 *
 * `RELAYED` is not set here even when peers are connected, because "a peer has stored
 * it" is a claim only the peer can make and the acknowledgement to carry it is day 9.
 * Inferring it from a connection count would be exactly the fake success §6.4 forbids.
 */
class SosTransmitter(private val context: Context) {

    /** §6.1 surfaces the fallback card at t+30 s with no channel having produced anything. */
    private val unreachableAfterMs = UNREACHABLE_AFTER_MS

    fun start(scope: CoroutineScope) {
        val repository = EventRepository(KaAlertoDatabase.getInstance(context).eventDao())
        val identity = LocalIdentity.getOrCreate(context)

        scope.launch {
            combine(repository.observeAll(), MeshState.status) { events, mesh ->
                SosReducer.activeMine(events, identity.authorId) to mesh
            }.collectLatest { (active, mesh) ->
                if (active == null) return@collectLatest

                val next = nextState(active.state, mesh)
                if (next != null) {
                    write(repository, identity, active, next)
                    // The write re-emits through observeAll and collectLatest restarts
                    // this block with the new state, so there is nothing more to do on
                    // this pass — and in particular the timer below must not run
                    // against a snapshot we have just superseded.
                    return@collectLatest
                }

                if (active.state.rank >= SosState.UNREACHABLE.rank) return@collectLatest
                // The deadline is absolute: startedAtMs + UNREACHABLE_AFTER_MS.
                // Using System.currentTimeMillis() - startedAtMs instead would compute
                // a different remaining time on every device that processes this event,
                // producing duplicate UNREACHABLE transitions in the log.
                val deadline = active.startedAtMs + unreachableAfterMs
                val remaining = deadline - System.currentTimeMillis()
                if (remaining > 0) delay(remaining)
                write(repository, identity, active, SosState.UNREACHABLE)
            }
        }
    }

    private fun nextState(current: SosState, mesh: MeshStatus): SosState? = when {
        current == SosState.QUEUED && mesh.running -> SosState.BEACONING
        else -> null
    }

    private suspend fun write(
        repository: EventRepository,
        identity: LocalIdentity.Identity,
        active: SosSnapshot,
        state: SosState,
    ) {
        // Monotonicity is enforced in the fold too, but checking here keeps the log
        // clean: without it, a re-emission would append an identical transition every
        // time the event table changed for any unrelated reason.
        if (mergeSosState(active.state, state) == active.state) return
        repository.insert(
            sosStateEvent(
                sosId = active.sosId,
                identity = identity,
                lat = active.lat,
                lon = active.lon,
                state = state,
                nowMs = System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        const val UNREACHABLE_AFTER_MS = 30_000L
    }
}
