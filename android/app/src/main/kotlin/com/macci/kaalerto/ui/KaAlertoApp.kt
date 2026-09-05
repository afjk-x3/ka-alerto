package com.macci.kaalerto.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.macci.kaalerto.data.haversineMeters
import com.macci.kaalerto.geofence.HomeLocationStore
import com.macci.kaalerto.location.fetchCurrentLocation
import com.macci.kaalerto.identity.LocalIdentity
import androidx.compose.runtime.rememberCoroutineScope
import com.macci.kaalerto.evac.EvacScreen
import com.macci.kaalerto.evac.evacStates
import com.macci.kaalerto.evac.loadEvacCentres
import com.macci.kaalerto.evac.submitEvacStatus
import com.macci.kaalerto.identity.RoleScreen
import com.macci.kaalerto.map.MapScreen
import com.macci.kaalerto.map.MapViewModel
import com.macci.kaalerto.official.OfficialStatusScreen
import com.macci.kaalerto.official.submitOfficialStatus
import kotlinx.coroutines.launch
import com.macci.kaalerto.mesh.MeshState
import com.macci.kaalerto.nav.Screen
import com.macci.kaalerto.report.ReportScreen
import com.macci.kaalerto.sos.RescueCardScreen
import com.macci.kaalerto.sos.SosAddContextRoute
import com.macci.kaalerto.sos.SosHoldScreen
import com.macci.kaalerto.sos.SosNearbyScreen
import com.macci.kaalerto.sos.SosQueueScreen
import com.macci.kaalerto.sos.SosState
import com.macci.kaalerto.sos.SosStatusScreen
import com.macci.kaalerto.sos.SosViewModel
import com.macci.kaalerto.sos.elapsedLabel
import kotlinx.coroutines.delay

/** Root screen switch — see [Screen] for why this isn't a navigation graph. */
@Composable
fun KaAlertoApp(
    modifier: Modifier = Modifier,
    stormMode: Boolean = false,
    onToggleStormMode: (() -> Unit)? = null,
    /** Set when the activity was opened by tapping day 9's nearby-SOS alert. */
    openSosId: String? = null,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Map) }
    val sosViewModel: SosViewModel = viewModel()
    val activeSos by sosViewModel.activeMine.collectAsStateWithLifecycle()
    val meshStatus by MeshState.status.collectAsStateWithLifecycle()
    val isResponder by sosViewModel.isResponder.collectAsStateWithLifecycle()
    val role by sosViewModel.role.collectAsStateWithLifecycle()
    val incoming by sosViewModel.incoming.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // One MapViewModel for the whole switch, so the official screen folds the same
    // event stream the map does rather than opening a second subscription.
    val mapViewModel: MapViewModel = viewModel()
    val mapEvents = remember { mapViewModel.events }

    // Tapping the alert lands on the request it was about, not on the map. A responder
    // goes straight to the queue; a resident gets the coarse nearby view.
    LaunchedEffect(openSosId) {
        val id = openSosId ?: return@LaunchedEffect
        screen = if (LocalIdentity.isResponder(context)) Screen.SosQueue else Screen.SosNearby(id)
    }

    // Which request has already had its rescue card raised for it. The card opens
    // itself once, when a request first goes UNREACHABLE — not every time the status
    // screen happens to be composed while it is still in that state. Without this the
    // card's "Bumalik" is a trap: it returns to the status screen, the effect below
    // fires again on the unchanged state, and the user is bounced straight back with no
    // way to reach "Ligtas na ako".
    var rescueCardRaisedFor by remember { mutableStateOf<String?>(null) }

    // One clock for every SOS screen's elapsed counter, rather than a ticker per screen.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeSos != null) {
        while (activeSos != null) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val snapshots by sosViewModel.snapshots.collectAsStateWithLifecycle()
    // `snapshots` starts empty and fills on the first emission of the event Flow. A
    // screen addressed by sosId must not read that initial empty list as "this request
    // does not exist" and bounce back to the map — which is exactly what the nearby-SOS
    // alert did: it routed correctly and was thrown straight back before a frame drew.
    val snapshotsLoaded = snapshots.isNotEmpty()
    fun snapshotFor(id: String) = snapshots.firstOrNull { it.sosId == id }

    when (val current = screen) {
        Screen.Map -> MapScreen(
            modifier = modifier,
            onStartReport = { lat, lon, accuracy -> screen = Screen.Report(lat, lon, accuracy) },
            onEnterPickLocation = { screen = Screen.PickLocation },
            // Day 4's conflict sheet: "I-check ko ngayon" files a fresh report at the
            // conflicted spot rather than a confirm/dispute — see detail/DetailSheet.kt.
            onStartReportAt = { lat, lon -> screen = Screen.Report(lat, lon, null) },
            // Day 8: an already-running request reopens its status rather than starting
            // a second one — five people pressing SOS is one rescue
            // (docs/03-architecture.md §6.5, duplicate collapse), and the same person
            // pressing twice certainly is.
            onStartSos = { lat, lon, accuracy ->
                val existing = activeSos
                screen = if (existing != null) Screen.SosStatus(existing.sosId) else Screen.SosHold(lat, lon, accuracy)
            },
            sosActive = activeSos != null,
            role = role,
            onOpenRoles = { screen = Screen.Roles },
            onOpenEvac = { screen = Screen.EvacCentres },
            onOpenOfficialStatus = { featureRef -> screen = Screen.OfficialStatus(featureRef) },
            stormMode = stormMode,
            onToggleStormMode = onToggleStormMode,
        )

        Screen.PickLocation -> MapScreen(
            modifier = modifier,
            pickMode = true,
            onLocationPicked = { latLng -> screen = Screen.Report(latLng.latitude, latLng.longitude, null) },
            onCancelPick = { screen = Screen.Map },
        )

        is Screen.Report -> key(current) {
            // Keyed on the whole Screen.Report value (not just its call site) so a
            // fresh location — GPS retry, a picked point — always starts the form
            // (mode, selected depth, severity override) from scratch instead of
            // Compose reusing the previous instance's `remember` state, which is what
            // was happening here: a form filled out for one location could otherwise
            // survive into a different Screen.Report recomposition unchanged.
            ReportScreen(
                modifier = modifier,
                initialLat = current.lat,
                initialLon = current.lon,
                initialAccuracyMeters = current.accuracyMeters,
                onChangeLocation = { screen = Screen.PickLocation },
                onBack = { screen = Screen.Map },
                onSubmitted = { screen = Screen.Map },
            )
        }

        is Screen.SosHold -> SosHoldScreen(
            modifier = modifier,
            lat = current.lat,
            lon = current.lon,
            accuracyMeters = current.accuracyMeters,
            onHoldComplete = {
                sosViewModel.raise(current.lat, current.lon, current.accuracyMeters) { sosId ->
                    screen = Screen.SosAddContext(sosId)
                }
            },
            onCancel = { screen = Screen.Map },
        )

        is Screen.SosAddContext -> SosAddContextRoute(
            modifier = modifier,
            sosId = current.sosId,
            viewModel = sosViewModel,
            nowMs = nowMs,
            onDone = { screen = Screen.SosStatus(current.sosId) },
        )

        is Screen.SosStatus -> {
            val snapshot = snapshotFor(current.sosId)
            if (snapshot == null) {
                if (snapshotsLoaded) LaunchedEffect(current.sosId) { screen = Screen.Map }
            } else {
                // The rescue card is a state, not a tap (design/README.md): once no
                // channel has produced anything within the threshold, it raises itself —
                // once per request, see rescueCardRaisedFor.
                LaunchedEffect(snapshot.state, snapshot.sosId) {
                    if (snapshot.state == SosState.UNREACHABLE && rescueCardRaisedFor != snapshot.sosId) {
                        rescueCardRaisedFor = snapshot.sosId
                        screen = Screen.SosRescueCard(current.sosId)
                    }
                }
                SosStatusScreen(
                    modifier = modifier,
                    snapshot = snapshot,
                    meshStatus = meshStatus,
                    elapsedLabel = elapsedLabel(snapshot.startedAtMs, nowMs),
                    onMarkSafe = {
                        sosViewModel.close(current.sosId, SosState.SAFE_SELF_RESOLVED)
                        screen = Screen.Map
                    },
                    onShowRescueCard = { screen = Screen.SosRescueCard(current.sosId) },
                )
            }
        }

        is Screen.SosNearby -> {
            val snapshot = snapshotFor(current.sosId)
            if (snapshot == null) {
                if (snapshotsLoaded) LaunchedEffect(current.sosId) { screen = Screen.Map }
            } else {
                SosNearbyScreen(
                    modifier = modifier,
                    snapshot = snapshot,
                    distanceMeters = HomeLocationStore.get(context)?.let {
                        haversineMeters(it.lat, it.lon, snapshot.lat, snapshot.lon)
                    },
                    isResponder = isResponder,
                    onBecomeResponder = {
                        sosViewModel.setRole(LocalIdentity.ROLE_RESPONDER)
                        screen = Screen.SosQueue
                    },
                    onOpenQueue = { screen = Screen.SosQueue },
                    onBack = { screen = Screen.Map },
                )
            }
        }

        Screen.Roles -> RoleScreen(
            modifier = modifier,
            current = role,
            onSelect = { sosViewModel.setRole(it) },
            onBack = { screen = Screen.Map },
        )

        Screen.EvacCentres -> {
            val centres = remember { loadEvacCentres(context) }
            val events by mapEvents.collectAsStateWithLifecycle()
            // "Pinakamalapit muna" is this screen's whole premise, so it needs a point
            // to measure from. GPS first, saved home second — falling back to the map's
            // demo centre would print confident distances from somewhere the user is not.
            var origin by remember { mutableStateOf(HomeLocationStore.get(context)?.let { it.lat to it.lon }) }
            LaunchedEffect(Unit) {
                fetchCurrentLocation(context)?.let { origin = it.latitude to it.longitude }
            }
            val states = remember(centres, events, origin) {
                evacStates(centres, events, origin?.first, origin?.second)
            }
            EvacScreen(
                modifier = modifier,
                states = states,
                isOfficial = role == LocalIdentity.ROLE_OFFICIAL,
                onUpdate = { centreId, status, occupancy ->
                    val centre = centres.firstOrNull { it.id == centreId } ?: return@EvacScreen
                    scope.launch { submitEvacStatus(context, centre, status, occupancy) }
                },
                onBack = { screen = Screen.Map },
            )
        }

        is Screen.OfficialStatus -> {
            val summaries by mapViewModel.featureSummaries.collectAsStateWithLifecycle()
            val summary = summaries.firstOrNull { it.featureRef == current.featureRef }
            if (summary == null) {
                if (summaries.isNotEmpty()) LaunchedEffect(current.featureRef) { screen = Screen.Map }
            } else {
                OfficialStatusScreen(
                    modifier = modifier,
                    summary = summary,
                    officialName = LocalIdentity.getOrCreate(context).authorName,
                    onPost = { severity ->
                        scope.launch {
                            submitOfficialStatus(context, summary.featureRef, summary.lat, summary.lon, severity)
                        }
                        screen = Screen.Map
                    },
                    onBack = { screen = Screen.Map },
                )
            }
        }

        Screen.SosQueue -> SosQueueScreen(
            modifier = modifier,
            requests = incoming,
            myLat = HomeLocationStore.get(context)?.lat,
            myLon = HomeLocationStore.get(context)?.lon,
            onAcknowledge = { sosViewModel.advance(it, SosState.ACKNOWLEDGED) },
            onEnRoute = { sosViewModel.advance(it, SosState.EN_ROUTE) },
            onBack = { screen = Screen.Map },
        )

        is Screen.SosRescueCard -> {
            val snapshot = snapshotFor(current.sosId)
            if (snapshot == null) {
                if (snapshotsLoaded) LaunchedEffect(current.sosId) { screen = Screen.Map }
            } else {
                RescueCardScreen(
                    modifier = modifier,
                    snapshot = snapshot,
                    onBack = { screen = Screen.SosStatus(current.sosId) },
                )
            }
        }
    }
}
