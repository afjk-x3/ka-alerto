package com.macci.kaalerto.mesh

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.macci.kaalerto.MainActivity
import com.macci.kaalerto.R
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.notification.NotificationChannels
import com.macci.kaalerto.sos.redactForMesh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections

private const val TAG = "MeshService"
private const val NOTIFICATION_ID = 4201
private const val ACTION_STOP = "com.macci.kaalerto.mesh.STOP"

/**
 * BUILD_TASKS.md days 6-7 — the Nearby Connections mesh, hosted in a foreground service
 * so it keeps reconciling while the map is off screen.
 *
 * **What this is, precisely.** An anti-entropy gossip exchange over
 * `Strategy.P2P_CLUSTER`: every device both advertises and discovers, auto-accepts any
 * KaAlerto peer it meets, trades event-ID manifests, and sends only what the other side
 * is missing ([MeshProtocol]). Received events are inserted with `origin = "mesh"` and
 * `hopCount + 1`, and anything genuinely new is re-shared to every *other* connected
 * peer — that re-share is what makes it multi-hop rather than a pair of walkie-talkies:
 * phone C, out of A's range but inside B's, gets A's report through B.
 *
 * **Why the loop terminates.** Only events that were new to *this* device are re-shared.
 * A report coming back around a cycle hits `OnConflictStrategy.IGNORE` on the
 * content-hash primary key, counts as zero new events, and is not forwarded again.
 * [MESH_MAX_HOPS] is a second, independent bound.
 *
 * **What it deliberately is not.** Nothing is signed and no sender is verified — ground
 * rule 4 (no crypto, no auth). `onConnectionInitiated`'s `authenticationDigits` are
 * ignored rather than shown for out-of-band comparison, so a device on this mesh trusts
 * whatever a peer hands it. `docs/03-architecture.md` §407 describes the real thing
 * (CBOR, Ed25519-signed payloads, a BLE GATT tier for iOS); this is the hackathon
 * subset that BUILD_TASKS.md actually schedules.
 */
class MeshService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connections: ConnectionsClient by lazy { Nearby.getConnectionsClient(this) }
    private val repository: EventRepository by lazy {
        EventRepository(KaAlertoDatabase.getInstance(this).eventDao())
    }

    /**
     * Endpoints in a live `STATUS_OK` connection. Touched from Nearby's callbacks (main
     * thread) and from the IO coroutines that answer them, hence the synchronised set.
     */
    private val connected: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /**
     * Serialises the read-diff-insert cycle. Two peers can deliver overlapping batches
     * at the same moment; without this, both would read the same "already have" ID set,
     * both would count the same event as new, and both would re-share it.
     */
    private val exchangeLock = Mutex()

    private var started = false

    /** True between a successful `startAdvertising`/`startDiscovery` pair and the next teardown. */
    private var nearbyActive = false

    /**
     * Brings the mesh up and down as the radios come and go. This is the day 6-7
     * debugging note ("both devices need Bluetooth and Wi-Fi manually re-enabled after
     * airplane mode") implemented as behaviour rather than written down as a caveat: a
     * resident who switches Bluetooth back on gets a working relay within a second,
     * without knowing they were supposed to restart the app.
     */
    private val radioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = syncRadioState()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            radioReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(LocationManager.MODE_CHANGED_ACTION)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // startForeground must happen within a few seconds of the start request, before
        // any of the Nearby work below — otherwise the system kills us for not showing
        // the notification we promised.
        promoteToForeground(MeshState.status.value)

        if (!started) {
            started = true
            observeStatusForNotification()
            syncRadioState()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(radioReceiver) }
        stopNearby()
        MeshState.reset()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- Nearby lifecycle

    /**
     * The one place that decides whether Nearby should be running. Called on start and
     * on every Bluetooth/location state change, so the mesh follows the radios instead
     * of being decided once at launch.
     */
    private fun syncRadioState() {
        if (!MeshPermissions.allGranted(this)) {
            stopNearby()
            MeshState.setError("Kulang ang pahintulot para sa mesh")
            return
        }

        val readiness = MeshRadios.check(this)
        when {
            readiness.ready && !nearbyActive -> startNearby()
            !readiness.ready && nearbyActive -> {
                stopNearby()
                MeshState.setError(readiness.message.orEmpty())
            }
            !readiness.ready -> MeshState.setError(readiness.message.orEmpty())
        }
    }

    private fun stopNearby() {
        // stopAllEndpoints also tears down advertising and discovery.
        runCatching { connections.stopAllEndpoints() }
        connected.clear()
        nearbyActive = false
        // Without this, the notification falls through to "Naghahanap ng kalapit na
        // phone" — a false claim when the radio is actually off.  The error is cleared
        // by setRunning(true) when startNearby() succeeds again.
        MeshState.setError("Buksan ang Bluetooth para sa mesh")
    }

    private fun startNearby() {
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

        connections
            .startAdvertising(endpointName(), MESH_SERVICE_ID, connectionLifecycle, options)
            .addOnSuccessListener {
                connections
                    .startDiscovery(MESH_SERVICE_ID, endpointDiscovery, discoveryOptions)
                    .addOnSuccessListener {
                        nearbyActive = true
                        MeshState.setRunning(true)
                    }
                    .addOnFailureListener { error -> failStart("discovery", error) }
            }
            .addOnFailureListener { error -> failStart("advertising", error) }
    }

    private fun failStart(stage: String, error: Exception) {
        Log.w(TAG, "Nearby $stage failed", error)
        nearbyActive = false
        // The radio preconditions were already checked, so reaching here means something
        // this code can't name — say only that, rather than guessing at a cause and
        // sending the resident to toggle a setting that was never the problem.
        MeshState.setError("Hindi makapag-mesh ngayon")
    }

    /**
     * Nearby broadcasts this in the clear to anything scanning, so it is the device
     * model, not the resident's display name. The name is public *on a report* by an
     * explicit product decision (CLAUDE.md); that decision was about attribution of
     * something they chose to file, and does not extend to advertising who they are to
     * every stranger in Bluetooth range for as long as the app is installed.
     */
    private fun endpointName(): String = Build.MODEL ?: "KaAlerto"

    private val connectionLifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept, per BUILD_TASKS.md days 6-7. info.authenticationDigits is
            // deliberately unused: verifying it needs two people comparing numbers out
            // loud, which is the wrong interaction for a flood, and ground rule 4 puts
            // authentication out of scope for this build entirely.
            connections.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            when (resolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connected += endpointId
                    MeshState.setPeerCount(connected.size)
                    sendManifest(endpointId)
                }
                else -> Log.d(TAG, "Connection to $endpointId not established: ${resolution.status}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connected -= endpointId
            MeshState.setPeerCount(connected.size)
        }
    }

    private val endpointDiscovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != MESH_SERVICE_ID || endpointId in connected) return
            connections
                .requestConnection(endpointName(), endpointId, connectionLifecycle)
                // Both sides discover each other on P2P_CLUSTER, so both may request at
                // once and one request loses. That is normal, not an error worth
                // surfacing — the winning request still connects the pair.
                .addOnFailureListener { error -> Log.d(TAG, "requestConnection($endpointId) declined", error) }
        }

        override fun onEndpointLost(endpointId: String) = Unit
    }

    // ---------------------------------------------------------------- Message exchange

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            scope.launch { handleMessage(endpointId, bytes) }
        }

        // BYTES payloads arrive whole, so there is no partial-transfer state to track.
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private suspend fun handleMessage(endpointId: String, bytes: ByteArray) {
        val message = runCatching {
            meshJson.decodeFromString(MeshMessage.serializer(), bytes.decodeToString())
        }.getOrElse { error ->
            // A peer on a different protocol version, or a corrupted frame. Dropping one
            // payload must not take down the connection.
            Log.w(TAG, "Undecodable payload from $endpointId", error)
            return
        }

        when (message) {
            is MeshMessage.Manifest -> answerManifest(endpointId, message.ids.toSet())
            is MeshMessage.Events -> ingest(endpointId, message.events)
        }
    }

    /** Our half of the anti-entropy exchange: send what they're missing, nothing else. */
    private suspend fun answerManifest(endpointId: String, theirIds: Set<String>) {
        val now = System.currentTimeMillis()
        val diff = relayable(repository.all(), now).filter { it.id !in theirIds }
        sendEvents(setOf(endpointId), diff)
    }

    private suspend fun ingest(fromEndpointId: String, incoming: List<Event>) {
        val now = System.currentTimeMillis()

        val stored = exchangeLock.withLock {
            val fresh = acceptForStore(incoming, repository.allIds().toSet(), now)
            if (fresh.isNotEmpty()) repository.insert(fresh)
            fresh
        }

        if (stored.isEmpty()) return
        MeshState.addReceived(stored.size)

        // Multi-hop: pass on only what was new to us, and never back to the sender.
        // Re-filtered because the hop increment above may have pushed some to the limit.
        val onward = relayable(stored, now)
        sendEvents(connected.toSet() - fromEndpointId, onward)
    }

    private fun sendManifest(endpointId: String) {
        scope.launch {
            val payload = encode(MeshMessage.Manifest(repository.allIds()))
            connections.sendPayload(endpointId, Payload.fromBytes(payload))
        }
    }

    private fun sendEvents(endpointIds: Set<String>, events: List<Event>) {
        if (endpointIds.isEmpty() || events.isEmpty()) return
        // Day 9: strip an SOS's medical detail and the requester's name before it
        // leaves this device. §6.5 wants that payload encrypted; with no crypto in this
        // build the honest equivalent is not to send it at all — what is not sent
        // cannot be read off a relaying phone. See sos/SosMeshPolicy.kt.
        val outbound = events.map(::redactForMesh)
        for (batch in chunkForPayload(outbound)) {
            val payload = Payload.fromBytes(encode(MeshMessage.Events(batch)))
            connections.sendPayload(endpointIds.toList(), payload)
        }
    }

    private fun encode(message: MeshMessage): ByteArray =
        meshJson.encodeToString(MeshMessage.serializer(), message).toByteArray()

    // ----------------------------------------------------------------- Notification

    private fun observeStatusForNotification() {
        scope.launch {
            MeshState.status.collect { status -> promoteToForeground(status) }
        }
    }

    private fun promoteToForeground(status: MeshStatus) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(status),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            },
        )
    }

    private fun buildNotification(status: MeshStatus): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MeshService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val text = when {
            status.error != null -> status.error
            status.peerCount > 0 -> "${status.peerCount} kalapit na phone ang nakakonekta"
            else -> "Naghahanap ng kalapit na phone"
        }

        return NotificationCompat.Builder(this, NotificationChannels.CHANNEL_MESH)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Mesh ng KaAlerto")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp)
            .addAction(0, "Ihinto", stop)
            .build()
    }

    companion object {
        /**
         * No-ops unless every Nearby permission is held. A foreground notification
         * claiming the phone is reachable over mesh, sitting above a Nearby client that
         * was never allowed to start, is exactly the kind of false reassurance this
         * project refuses to ship.
         */
        fun start(context: Context) {
            if (!MeshPermissions.allGranted(context)) return
            ContextCompat.startForegroundService(context, Intent(context, MeshService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeshService::class.java))
        }
    }
}
