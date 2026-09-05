package com.macci.kaalerto.sos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Waits for the just-written request to come back through the event Flow before showing
 * the form.
 *
 * The gap is real but tiny: [SosViewModel.raise] navigates as soon as the insert
 * returns, and Room's Flow re-emits a moment later. Rendering an empty form against a
 * null snapshot for those few frames would mean the first answer had nothing to attach
 * itself to. Nothing is delayed by this — the request is already out; this only defers
 * the *optional* screen.
 */
@Composable
fun SosAddContextRoute(
    sosId: String,
    viewModel: SosViewModel,
    nowMs: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshots by viewModel.snapshots.collectAsStateWithLifecycle()
    val snapshot = snapshots.firstOrNull { it.sosId == sosId }

    if (snapshot == null) return

    SosContextScreen(
        modifier = modifier,
        snapshot = snapshot,
        elapsedLabel = elapsedLabel(snapshot.startedAtMs, nowMs),
        onAmend = { viewModel.amend(sosId, it) },
        onDone = onDone,
    )
}
