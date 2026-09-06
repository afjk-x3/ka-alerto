package com.macci.kaalerto.identity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the role fold for the UI, and is the only thing that writes
 * [LocalIdentity.cacheDerivedRole].
 *
 * Note what the event-sourced path does *not* have: a setter. `claimSeat`,
 * `applyAsResponder`, `grant` and `revoke` each append an event and then wait for the
 * fold to come back around through the database Flow, exactly like filing a report does.
 * That round trip is the point — it is what makes this device's idea of its own role the
 * same object every other device computes, rather than a private string that happened to
 * agree.
 *
 * [setRoleForTesting] is the one exception, and it exists only while
 * [RoleMode.EVENT_SOURCED] is `false`. See [RoleMode] for why, and for what to delete
 * when the flag flips.
 */
class RoleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EventRepository(KaAlertoDatabase.getInstance(application).eventDao())

    val roster: List<BarangaySeat> = loadBarangayRoster(application)

    val state: StateFlow<RoleState> = repository.observeAll()
        .map { events -> foldRoles(events, roster) }
        // The cache is refreshed here rather than in each collector so that it is
        // updated even while no role screen is on top — a grant can arrive over the
        // mesh while the resident is looking at the map, and the next report they file
        // must already carry the new role.
        //
        // Skipped entirely in manual mode: the fold still runs (so the flow keeps
        // being exercised and does not rot behind the flag) but it must not overwrite
        // a role the tester set by hand.
        .onEach {
            if (RoleMode.EVENT_SOURCED) {
                LocalIdentity.cacheDerivedRole(getApplication(), it.roleOf(myAuthorId))
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RoleState())

    val myAuthorId: String get() = LocalIdentity.getOrCreate(getApplication()).authorId

    /** Backs [role] while [RoleMode.EVENT_SOURCED] is false. Unused when it is true. */
    private val manualRole = MutableStateFlow(LocalIdentity.role(application))

    val role: StateFlow<String> =
        if (RoleMode.EVENT_SOURCED) {
            state.map { it.roleOf(myAuthorId) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, LocalIdentity.role(application))
        } else {
            manualRole.asStateFlow()
        }

    /**
     * Day 10's self-grant, reachable only in manual mode. Re-reads nothing else: the
     * display name is re-formed from the role on the next [LocalIdentity.getOrCreate],
     * so an event authored after this carries both the new role and the matching name.
     */
    fun setRoleForTesting(role: String) {
        if (RoleMode.EVENT_SOURCED) return
        LocalIdentity.setRoleForTesting(getApplication(), role)
        manualRole.value = role
    }

    /** Responder *or* official — both see the day 9 rescue queue. */
    val isResponder: StateFlow<Boolean> = role
        .map { it != LocalIdentity.ROLE_RESIDENT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalIdentity.isResponder(application))

    /** Seats nobody has claimed yet — what this device is offered. */
    fun unclaimedSeats(current: RoleState): List<BarangaySeat> =
        roster.filter { seat -> current.seats.none { it.seatId == seat.id } }

    /** Refused if somebody already holds the seat — contesting one is not a tap away. */
    fun claimSeat(seat: BarangaySeat) {
        if (state.value.seats.any { it.seatId == seat.id }) return
        append { identity, now -> roleClaimEvent(identity, seat, now) }
    }

    fun applyAsResponder() {
        if (isResponder.value || state.value.hasApplied(myAuthorId)) return
        append { identity, now -> roleRequestEvent(identity, now) }
    }

    fun grant(application: RoleApplication) = asOfficial { identity, now ->
        roleGrantEvent(identity, application.authorId, application.authorName, now)
    }

    fun revoke(grant: RoleGrantRecord) = asOfficial { identity, now ->
        roleRevokeEvent(identity, grant.subjectId, grant.subjectName, now)
    }

    /**
     * Guarded here as well as in [foldRoles], for a different reason each time. The fold
     * refuses an unauthorised grant *received* from anywhere, which is what protects the
     * barangay. This refuses to author one in the first place, which protects the user:
     * without it a UI bug could put a grant on the mesh that every other device
     * correctly ignores, leaving this one device alone in believing it worked — the
     * worst failure shape for a system whose claim is that all phones agree.
     */
    private fun asOfficial(build: (LocalIdentity.Identity, Long) -> com.macci.kaalerto.data.Event) {
        if (state.value.roleOf(myAuthorId) != LocalIdentity.ROLE_OFFICIAL) return
        append(build)
    }

    private fun append(build: (LocalIdentity.Identity, Long) -> com.macci.kaalerto.data.Event) {
        viewModelScope.launch {
            val identity = LocalIdentity.getOrCreate(getApplication())
            repository.insert(build(identity, System.currentTimeMillis()))
        }
    }
}
