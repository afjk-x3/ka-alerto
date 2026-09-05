package com.macci.kaalerto.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * @param running The service is up and Nearby accepted both `startAdvertising` and
 *   `startDiscovery`. False while stopped, and false if either call was rejected.
 * @param peerCount Endpoints currently in a `STATUS_OK` connection — BUILD_TASKS.md
 *   days 6-7's "3 nearby phones connected".
 * @param receivedCount Events this device has taken in over the mesh since the service
 *   started. Only counts genuinely new ones; a re-delivery the dedup dropped is not a
 *   report that arrived.
 * @param error Why the mesh isn't working, in words fit to show a resident. Null when
 *   there's nothing wrong.
 */
data class MeshStatus(
    val running: Boolean = false,
    val peerCount: Int = 0,
    val receivedCount: Int = 0,
    val error: String? = null,
)

/**
 * Process-global mesh state, read by the UI and written by [MeshService].
 *
 * A bound service would be the textbook way for a composable to read a service's state,
 * but the service and the Compose tree live in the same process and this is one small
 * immutable value — binding, a `ServiceConnection`, and an AIDL-shaped interface is far
 * more machinery than the fact deserves. The trade-off is that this survives the
 * service being destroyed, so [reset] is called from `onDestroy` rather than relying on
 * the object going away.
 */
object MeshState {
    private val _status = MutableStateFlow(MeshStatus())
    val status: StateFlow<MeshStatus> = _status.asStateFlow()

    fun setRunning(running: Boolean) = _status.update { it.copy(running = running, error = null) }

    fun setPeerCount(count: Int) = _status.update { it.copy(peerCount = count) }

    fun addReceived(count: Int) = _status.update { it.copy(receivedCount = it.receivedCount + count) }

    fun setError(message: String) = _status.update { it.copy(running = false, peerCount = 0, error = message) }

    fun reset() = _status.update { MeshStatus() }
}
