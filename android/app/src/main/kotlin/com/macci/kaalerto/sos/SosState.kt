package com.macci.kaalerto.sos

import kotlinx.serialization.Serializable

/**
 * The SOS lifecycle from `docs/03-architecture.md` §6.2, as BUILD_TASKS.md day 8 lists
 * it: QUEUED → BEACONING → RELAYED → DELIVERED → ACKNOWLEDGED → EN_ROUTE → RESCUED,
 * plus CANCELLED and SAFE_SELF_RESOLVED. `ON_SCENE` and `UNREACHABLE` come from the
 * architecture doc's own diagram, which is the deeper reference where the two differ.
 *
 * [rank] is strictly increasing along every arrow in that diagram, including the ones
 * that skip rungs (`QUEUED → DELIVERED` when the server answers before any peer is
 * found) and the ones through `UNREACHABLE`. That single property is what implements
 * the doc's rule that **transitions are monotonic — a late-arriving lower state cannot
 * regress a higher one** — which matters because these states travel as events over a
 * mesh that makes no ordering promise at all.
 *
 * `EXPIRED` is deliberately absent. It is an *escalation* decision (§6.5: no
 * acknowledgement inside the barangay window → widen the radius), which needs a server
 * and a responder hierarchy that do not exist in this build. A state that nothing can
 * ever set would just be a lie in an enum.
 */
@Serializable
enum class SosState(val rank: Int) {
    /** Being composed. Never written as an event — it exists only before the hold completes. */
    DRAFT(0),

    /** Written to local storage. This happens at t+0, before any transmission is attempted. */
    QUEUED(1),

    /** The mesh radio is live and this request is in the table peers reconcile against. */
    BEACONING(2),

    /**
     * No channel has produced a relay or a delivery within the threshold
     * (`docs/03-architecture.md` §6.1: t+30 s). Not a failure state — the phone keeps
     * broadcasting. It is what raises the rescue card, which is a *state*, not a tap
     * (design/README.md).
     */
    UNREACHABLE(3),

    /** At least one peer has stored the request and is re-broadcasting it. */
    RELAYED(4),

    /** A server or SMS gateway confirmed receipt. */
    DELIVERED(5),

    /** A named responder or LGU claimed it. */
    ACKNOWLEDGED(6),

    EN_ROUTE(7),

    ON_SCENE(8),

    RESCUED(9),

    /** Stood down before anyone was dispatched. */
    CANCELLED(10),

    /** The requester marked themselves safe. */
    SAFE_SELF_RESOLVED(11),
    ;

    /** Nothing reopens a closed request — a later transition of any kind is ignored. */
    val isClosed: Boolean get() = this == RESCUED || this == CANCELLED || this == SAFE_SELF_RESOLVED

    /**
     * Whether this build can actually reach this state. Everything past [BEACONING]
     * needs a transport or a counterparty that is not built yet: [RELAYED] and
     * [ACKNOWLEDGED] are day 9, [DELIVERED] needs SMS (day 12) or the server (day 13),
     * and [EN_ROUTE] onward need a responder app that is not in scope at all.
     *
     * The UI reads this so it can say so, instead of rendering a ladder that silently
     * never advances.
     */
    val reachableInThisBuild: Boolean
        get() = this in setOf(DRAFT, QUEUED, BEACONING, UNREACHABLE, CANCELLED, SAFE_SELF_RESOLVED)
}

/**
 * Folds one incoming state into the current one, monotonically.
 *
 * Order of the two closed checks matters: a request that is already closed stays
 * closed (a peer relaying a stale BEACONING must not reopen someone's finished
 * rescue), but an *incoming* close always wins, because "I am safe" and "cancelled"
 * have to be able to land on top of any progress state.
 */
fun mergeSosState(current: SosState, incoming: SosState): SosState = when {
    current.isClosed -> current
    incoming.isClosed -> incoming
    incoming.rank > current.rank -> incoming
    else -> current
}
