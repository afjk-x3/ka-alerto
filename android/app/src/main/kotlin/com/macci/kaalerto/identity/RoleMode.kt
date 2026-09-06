package com.macci.kaalerto.identity

/**
 * Which of the two role systems is live.
 *
 * The event-sourced flow ([foldRoles], [RoleScreen]) is built, tested and verified on
 * device, but it is **one-way by design**: claiming a seat writes an event and there is
 * no un-claim, and `role_revoke` stands down a responder rather than a seat holder. That
 * is correct for a barangay and wrong for a bench, because it means a single device
 * cannot walk through the resident, responder and official screens in one sitting.
 *
 * So it is parked here behind a flag while the app is being exercised by hand. Flip this
 * to `true` to switch the whole app over — the fold, the screens and the guards all key
 * off it, and nothing else needs editing.
 *
 * **This is not a permanent fork.** The manual path is the superseded day-10 toggle,
 * kept only for testing, and it is the thing the project's own decision table says roles
 * must not be. When manual testing is done, set this to `true` and delete
 * [ManualRoleScreen] along with [LocalIdentity.setRoleForTesting].
 */
object RoleMode {

    /**
     * `false` — day 10's self-select toggle. Any role reachable in two taps, nothing
     * replicates, and the screen says so.
     *
     * `true` — the 6 September activation flow. Residents apply, officials activate,
     * official comes only from claiming a roster seat, and every device folds the same
     * answer from the event log.
     */
    const val EVENT_SOURCED = false
}
