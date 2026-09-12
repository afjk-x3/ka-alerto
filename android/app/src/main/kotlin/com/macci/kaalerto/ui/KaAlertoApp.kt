package com.macci.kaalerto.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.macci.kaalerto.data.haversineMeters
import com.macci.kaalerto.geofence.HomeLocationStore
import com.macci.kaalerto.location.describePlace
import com.macci.kaalerto.location.fetchAccurateLocation
import com.macci.kaalerto.location.fetchCurrentLocation
import org.maplibre.android.geometry.LatLng
import com.macci.kaalerto.i18n.AppLanguage
import com.macci.kaalerto.i18n.LanguagePrefs
import com.macci.kaalerto.i18n.LocalAppLanguage
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.identity.displayFormOf
import androidx.compose.runtime.rememberCoroutineScope
import com.macci.kaalerto.evac.EvacScreen
import com.macci.kaalerto.evac.evacStates
import com.macci.kaalerto.evac.loadEvacCentres
import com.macci.kaalerto.evac.submitEvacStatus
import com.macci.kaalerto.identity.RoleScreen
import com.macci.kaalerto.map.MapScreen
import com.macci.kaalerto.map.HOME_REGION_NAME
import com.macci.kaalerto.map.MapViewModel
import com.macci.kaalerto.map.OfflineMapPack
import com.macci.kaalerto.map.boundsAround
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
import com.macci.kaalerto.demo.DemoArea
import com.macci.kaalerto.identity.ManualRoleScreen
import com.macci.kaalerto.identity.OnboardingScreen
import com.macci.kaalerto.identity.ProfileScreen
import com.macci.kaalerto.identity.RoleMode
import com.macci.kaalerto.identity.RoleViewModel
import com.macci.kaalerto.nav.NavDrawer
import com.macci.kaalerto.sos.SosViewModel
import com.macci.kaalerto.sos.elapsedLabel
import com.macci.kaalerto.family.CircleCard
import com.macci.kaalerto.family.FamilyCircleScreen
import com.macci.kaalerto.family.QrScannerScreen
import com.macci.kaalerto.family.circleStatuses
import com.macci.kaalerto.family.effectiveCircle
import com.macci.kaalerto.family.encode
import com.macci.kaalerto.family.submitCheckIn
import com.macci.kaalerto.family.submitCircleInvite
import com.macci.kaalerto.sync.ServerDiscoveryClient
import com.macci.kaalerto.sync.SyncPrefs
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
    val appContext = LocalContext.current
    // PRD §9 is literal: registration is required at first run, so an unregistered
    // device opens on the form rather than the map. The SOS banner on that form stops
    // being a courtesy at this point and becomes the only thing reachable — which is
    // exactly why it is there, and why it must never be gated.
    var screen by remember {
        mutableStateOf<Screen>(
            if (LocalIdentity.isRegistered(appContext)) Screen.Map else Screen.Onboarding(Screen.Map),
        )
    }
    // The registration draft lives here, not inside the screen: confirming the home pin
    // navigates to the map and back, and a name typed before that must survive the trip.
    var draftFirstName by remember { mutableStateOf(LocalIdentity.registeredFirstName(appContext)) }
    var draftLastName by remember { mutableStateOf(LocalIdentity.registeredLastName(appContext)) }
    var draftBarangay by remember {
        mutableStateOf(LocalIdentity.homeBarangay(appContext).ifBlank { DemoArea.BARANGAY_NAME })
    }
    // The barangay follows the pin until somebody corrects it, and then stops following:
    // a field that keeps overwriting a correction is worse than one that never filled
    // itself, because the person has already told the app it was wrong once.
    var barangayCorrected by remember { mutableStateOf(LocalIdentity.homeBarangay(appContext).isNotBlank()) }
    var barangayFromLocation by remember { mutableStateOf(false) }
    var draftHome by remember {
        mutableStateOf(HomeLocationStore.get(appContext)?.let { it.lat to it.lon })
    }
    var draftAccuracy by remember { mutableStateOf<Float?>(null) }
    var draftPhone by remember { mutableStateOf(LocalIdentity.registeredPhone(appContext)) }
    var draftServerUrl by remember { mutableStateOf(SyncPrefs.getServerUrl(appContext) ?: "") }
    var discoveringServer by remember { mutableStateOf(false) }
    // True only while the current draftServerUrl is exactly what discovery found and
    // untouched since — typing over it (see onServerUrlChange below) clears this, so the
    // status line never claims "auto-found" about something someone has since edited.
    var serverAutoDetected by remember { mutableStateOf(false) }
    // The hamburger drawer, shared by every screen that shows one — see NavDrawer.kt
    // for why this lives here rather than being duplicated per screen.
    var drawerOpen by remember { mutableStateOf(false) }
    // Persisted (i18n/Strings.kt) so picking English survives a cold restart — nobody
    // wants to re-toggle it every launch. Filipino is the base language everywhere else
    // in this file and every screen; this is the one place that can override it.
    var language by remember { mutableStateOf(LanguagePrefs.get(appContext)) }
    // PickHome is a standalone top-level screen with no resume field of its own. It is
    // reachable from both Onboarding (a first run) and Profile (an edit), so this holds
    // whichever of those two screen instances is currently open, captured on entry to
    // either branch below — without it, picking a location would drop the real origin
    // and land back on the map instead of the screen "Ituro sa mapa" was opened from.
    var pickHomeReturn by remember { mutableStateOf<Screen>(Screen.Onboarding(Screen.Map)) }
    // Set by an evac-centre card tap, consumed once by the very next Screen.Map mount —
    // see the LaunchedEffect(Unit) at that branch. A tap should move the camera exactly
    // once, not pin every later visit to the map on a shelter the user tapped an hour ago.
    var evacFocusCamera by remember { mutableStateOf<LatLng?>(null) }
    var draftPlaceName by remember { mutableStateOf<String?>(null) }
    var locatingHome by remember { mutableStateOf(false) }
    val sosViewModel: SosViewModel = viewModel()
    val activeSos by sosViewModel.activeMine.collectAsStateWithLifecycle()
    val meshStatus by MeshState.status.collectAsStateWithLifecycle()
    // Day 10's roles, rebuilt as a fold over the event log (identity/RoleReducer.kt).
    // This device's role is derived, never set: what appears here is what every other
    // phone in the barangay computes from the same events.
    val roleViewModel: RoleViewModel = viewModel()
    val roleState by roleViewModel.state.collectAsStateWithLifecycle()
    val isResponder by roleViewModel.isResponder.collectAsStateWithLifecycle()
    val role by roleViewModel.role.collectAsStateWithLifecycle()
    val incoming by sosViewModel.incoming.collectAsStateWithLifecycle()
    val context = appContext
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

    // A device with its own SOS still open must not reopen on a map that shows no sign
    // of it — the rescue card only auto-raises past the 30s UNREACHABLE threshold
    // (below), so a phone closed and reopened before that would otherwise land
    // somewhere that looks like nothing is happening. Checked once: `activeSos` starts
    // null until the very first fold of the event log resolves, so this fires again as
    // that real value arrives, but never redirects a second time — a *new* SOS started
    // later in the same session must not yank the user away from wherever they are.
    var initialSosRedirectDone by remember { mutableStateOf(false) }
    LaunchedEffect(activeSos) {
        val sos = activeSos
        if (!initialSosRedirectDone && sos != null && screen == Screen.Map) {
            initialSosRedirectDone = true
            screen = Screen.SosStatus(sos.sosId)
        }
    }

    // Which request has already had its rescue card raised for it. The card opens
    // itself once, when a request first goes UNREACHABLE — not every time the status
    // screen happens to be composed while it is still in that state. Without this the
    // card's "Bumalik" is a trap: it returns to the status screen, the effect below
    // fires again on the unchanged state, and the user is bounced straight back with no
    // way to reach "Ligtas na ako".
    var rescueCardRaisedFor by remember { mutableStateOf<String?>(null) }

    // The pin finds itself. Runs on entry to the form and on an explicit retry, and
    // is bounded by the fetcher's own 6 s budget, so a phone with no lock lands on
    // "Ituro na lang sa mapa" rather than a spinner that never resolves.
    // The label and the barangay both follow the pin, whether it came from GPS or from a
    // tap on the map — moving the pin and leaving the barangay behind would quietly file
    // reports against the wrong place.
    LaunchedEffect(draftHome) {
        val pin = draftHome
        val place = if (pin == null) null else describePlace(appContext, pin.first, pin.second)
        draftPlaceName = place?.label
        val resolved = place?.barangay
        if (resolved != null && !barangayCorrected) {
            draftBarangay = resolved
            barangayFromLocation = true
        }
    }

    val onIdentityScreen = screen is Screen.Onboarding || screen is Screen.Profile
    LaunchedEffect(onIdentityScreen) {
        if (onIdentityScreen && draftHome == null && !locatingHome) {
            locatingHome = true
            val fix = fetchAccurateLocation(appContext)
            if (fix != null) {
                draftHome = fix.latitude to fix.longitude
                draftAccuracy = fix.accuracy
            }
            locatingHome = false
        }
    }

    // Auto-tries LAN discovery once on opening Profile with the field still empty —
    // never on Onboarding, which has no server field to show a result in. A pre-fill
    // only: draftServerUrl is a draft like every other field here, and "I-save" is what
    // actually commits it. Guarded so a fresh, still-empty field left by discovery
    // finding nothing does not retrigger on every recomposition.
    val onProfileScreen = screen is Screen.Profile
    LaunchedEffect(onProfileScreen) {
        if (onProfileScreen && draftServerUrl.isBlank() && !discoveringServer) {
            discoveringServer = true
            val found = ServerDiscoveryClient.discover()
            if (found != null && draftServerUrl.isBlank()) {
                draftServerUrl = found
                serverAutoDetected = true
            }
            discoveringServer = false
        }
    }
    // The feature whose sheet the registration gate interrupted, reopened on return.
    var reopenFeatureRef by remember { mutableStateOf<String?>(null) }

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

    /**
     * PRD §9's gate. Anything that *authors* an event under this device's name goes
     * through here first; reading the map, and the whole SOS path, never do.
     *
     * The screen it would have gone to is carried into [Screen.Onboarding] so finishing
     * the form resumes it — the interrupted action may be somebody standing in rising
     * water, and making them find their way back to it is not a neutral cost.
     */
    fun gated(destination: Screen): Screen =
        if (LocalIdentity.isRegistered(context)) destination else Screen.Onboarding(destination)

    CompositionLocalProvider(LocalAppLanguage provides language) {
    // Edge-to-edge is on (MainActivity.kt) so the OS draws status/nav bars translucent
    // over the window instead of reserving space for them — done once here, at the root
    // of every screen, rather than per screen, so nothing new can reintroduce the
    // overlap. RescueCardScreen's full-black background still paints edge to edge behind
    // the (now inset) content; only the content itself moves, not the color.
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
    when (val current = screen) {
        Screen.Map -> {
        val focusCamera = evacFocusCamera
        LaunchedEffect(Unit) { if (focusCamera != null) evacFocusCamera = null }
        MapScreen(
            modifier = modifier,
            initialCamera = focusCamera,
            onStartReport = { lat, lon, accuracy -> screen = gated(Screen.Report(lat, lon, accuracy)) },
            onEnterPickLocation = { screen = gated(Screen.PickLocation) },
            // Day 4's conflict sheet: "I-check ko ngayon" files a fresh report at the
            // conflicted spot rather than a confirm/dispute — see detail/DetailSheet.kt.
            onStartReportAt = { lat, lon -> screen = gated(Screen.Report(lat, lon, null)) },
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
            onOpenEvac = { screen = Screen.EvacCentres },
            onOpenOfficialStatus = { featureRef -> screen = Screen.OfficialStatus(featureRef) },
            // The rescue queue's only other way in is an incoming SOS alert, so without
            // this a responder with no live emergency cannot reach the screen their role
            // exists for. Null for a resident, which is what hides the strip.
            onOpenQueue = if (isResponder) ({ screen = Screen.SosQueue }) else null,
            openRequestCount = incoming.size,
            onNeedsRegistration = if (LocalIdentity.isRegistered(context)) {
                null
            } else {
                { featureRef ->
                    reopenFeatureRef = featureRef
                    screen = Screen.Onboarding(Screen.Map)
                }
            },
            focusFeatureRef = reopenFeatureRef,
            stormMode = stormMode,
            onToggleStormMode = onToggleStormMode,
            onOpenMenu = { drawerOpen = true },
        )
        }

        Screen.PickLocation -> MapScreen(
            modifier = modifier,
            pickMode = true,
            onLocationPicked = { latLng -> screen = gated(Screen.Report(latLng.latitude, latLng.longitude, null)) },
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
                // Reuses the same "reopen where you left off" state as the
                // registration-gate resume above — landing on the map with the
                // just-filed report's sheet already open is the status indicator: its
                // confidence bucket and delivery card (detail/DetailSheet.kt) are real,
                // existing UI, not a new fabricated "submitted!" toast.
                onSubmitted = { featureRef ->
                    reopenFeatureRef = featureRef
                    screen = Screen.Map
                },
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
                        if (RoleMode.EVENT_SOURCED) {
                            // No longer a self-grant. Applying is all a resident can do;
                            // the role screen is where they find out if anyone answered.
                            roleViewModel.applyAsResponder()
                            screen = Screen.Roles
                        } else {
                            roleViewModel.setRoleForTesting(LocalIdentity.ROLE_RESPONDER)
                            screen = Screen.SosQueue
                        }
                    },
                    onOpenQueue = { screen = Screen.SosQueue },
                    onBack = { screen = Screen.Map },
                    onOpenMenu = { drawerOpen = true },
                )
            }
        }

        // Two role screens, one flag. See identity/RoleMode.kt — the event-sourced flow
        // is one-way by design, which is right for a barangay and wrong for a bench, so
        // it is parked while the app is being exercised by hand.
        Screen.PickHome -> MapScreen(
            modifier = modifier,
            pickMode = true,
            pickingHome = true,
            // Open on the pin being corrected, and show where this phone actually is.
            // Without both, somebody is asked to check a pin against a map of a place
            // they are not in, with no dot to check it against.
            // MapScreen turns the blue dot on from the permission state itself, so
            // there is nothing to pass — the dot was never missing here, it was
            // off-screen because the camera was in the wrong hemisphere of the country.
            initialCamera = draftHome?.let { (lat, lon) -> LatLng(lat, lon) },
            onLocationPicked = { latLng ->
                draftHome = latLng.latitude to latLng.longitude
                // Hand-placed, so the GPS accuracy no longer describes it.
                draftAccuracy = null
                screen = pickHomeReturn
            },
            onCancelPick = { screen = pickHomeReturn },
        )

        is Screen.Onboarding -> {
            // Captured on every recomposition of this branch, so PickHome (a separate
            // top-level screen — see pickHomeReturn above) can restore it later.
            pickHomeReturn = current
            OnboardingScreen(
            modifier = modifier,
            firstName = draftFirstName,
            onFirstNameChange = { draftFirstName = it },
            lastName = draftLastName,
            onLastNameChange = { draftLastName = it },
            barangay = draftBarangay,
            onBarangayChange = {
                draftBarangay = it
                barangayCorrected = true
                barangayFromLocation = false
            },
            barangayFromLocation = barangayFromLocation,
            home = draftHome,
            accuracyMeters = draftAccuracy,
            placeName = draftPlaceName,
            locating = locatingHome,
            onLocate = {
                scope.launch {
                    locatingHome = true
                    val fix = fetchAccurateLocation(appContext)
                    if (fix != null) {
                        draftHome = fix.latitude to fix.longitude
                        draftAccuracy = fix.accuracy
                    }
                    locatingHome = false
                }
            },
            onPickOnMap = { screen = Screen.PickHome },
            // Resuming, not just dismissing — see `gated`.
            onDone = {
                LocalIdentity.register(context, draftFirstName, draftLastName, draftPhone, draftBarangay)
                // Registering also sets day 5's home radius, so the notification primer
                // on this very screen is true from the first launch instead of waiting
                // for somebody to discover the map long-press.
                draftHome?.let { (lat, lon) ->
                    HomeLocationStore.set(context, lat, lon, HomeLocationStore.DEFAULT_RADIUS_METERS)
                    // A second pack, around wherever this person actually lives. Only
                    // when they are outside the frozen demo area — inside it the demo
                    // pack already covers them, and a duplicate would be wasted bytes.
                    //
                    // Registration is the right moment and close to the only one: the
                    // artboard's "I-download habang may signal pa" is exactly this, and
                    // somebody who has just installed the app is the likeliest they will
                    // ever be to have a connection.
                    if (!DemoArea.bounds.contains(LatLng(lat, lon))) {
                        OfflineMapPack(
                            appContext,
                            regionName = HOME_REGION_NAME,
                            bounds = boundsAround(lat, lon),
                        ).ensureDownloaded()
                    }
                }
                screen = current.resume ?: Screen.Map
            },
            // The escape hatch is the whole reason the gate is defensible: nobody is
            // ever held behind this form during an emergency.
            // Use fetchAccurateLocation (15s window, degrades to 6s one-shot) for consistency
            // with the registration screen's own location stream — same hardware scenario.
            onSos = {
                scope.launch {
                    val location = fetchAccurateLocation(context)
                    val existing = activeSos
                    screen = if (existing != null) {
                        Screen.SosStatus(existing.sosId)
                    } else {
                        Screen.SosHold(
                            location?.latitude ?: DemoArea.centre.latitude,
                            location?.longitude ?: DemoArea.centre.longitude,
                            location?.accuracy,
                        )
                    }
                }
            },
        )
        }

        is Screen.Profile -> {
            // Same reasoning as the Onboarding branch above: PickHome needs to know
            // which of the two screens sent it here.
            pickHomeReturn = current
            ProfileScreen(
                modifier = modifier,
                firstName = draftFirstName,
                onFirstNameChange = { draftFirstName = it },
                lastName = draftLastName,
                onLastNameChange = { draftLastName = it },
                phone = draftPhone,
                onPhoneChange = { draftPhone = it },
                serverUrl = draftServerUrl,
                onServerUrlChange = {
                    draftServerUrl = it
                    serverAutoDetected = false
                },
                lastSyncedAtMs = SyncPrefs.getLastSyncedAtMs(context),
                serverSearching = discoveringServer,
                serverAutoDetected = serverAutoDetected,
                onSearchServer = {
                    scope.launch {
                        discoveringServer = true
                        val found = ServerDiscoveryClient.discover()
                        if (found != null) {
                            draftServerUrl = found
                            serverAutoDetected = true
                        }
                        discoveringServer = false
                    }
                },
                barangay = draftBarangay,
                onBarangayChange = {
                    draftBarangay = it
                    barangayCorrected = true
                    barangayFromLocation = false
                },
                barangayFromLocation = barangayFromLocation,
                home = draftHome,
                accuracyMeters = draftAccuracy,
                placeName = draftPlaceName,
                locating = locatingHome,
                onLocate = {
                    scope.launch {
                        locatingHome = true
                        val fix = fetchAccurateLocation(appContext)
                        if (fix != null) {
                            draftHome = fix.latitude to fix.longitude
                            draftAccuracy = fix.accuracy
                        }
                        locatingHome = false
                    }
                },
                onPickOnMap = { screen = Screen.PickHome },
                onSave = {
                    LocalIdentity.register(context, draftFirstName, draftLastName, draftPhone, draftBarangay)
                    SyncPrefs.setServerUrl(context, draftServerUrl)
                    draftHome?.let { (lat, lon) ->
                        HomeLocationStore.set(context, lat, lon, HomeLocationStore.DEFAULT_RADIUS_METERS)
                        if (!DemoArea.bounds.contains(LatLng(lat, lon))) {
                            OfflineMapPack(
                                appContext,
                                regionName = HOME_REGION_NAME,
                                bounds = boundsAround(lat, lon),
                            ).ensureDownloaded()
                        }
                    }
                    screen = current.resume
                },
                onCancel = { screen = current.resume },
                onOpenMenu = { drawerOpen = true },
            )
        }

        Screen.Roles -> if (RoleMode.EVENT_SOURCED) {
            RoleScreen(
                modifier = modifier,
                state = roleState,
                myAuthorId = roleViewModel.myAuthorId,
                unclaimedSeats = roleViewModel.unclaimedSeats(roleState),
                onClaimSeat = { roleViewModel.claimSeat(it) },
                onApply = { roleViewModel.applyAsResponder() },
                onGrant = { roleViewModel.grant(it) },
                onRevoke = { roleViewModel.revoke(it) },
                onBack = { screen = Screen.Map },
                onOpenMenu = { drawerOpen = true },
            )
        } else {
            ManualRoleScreen(
                modifier = modifier,
                current = role,
                onSelect = { roleViewModel.setRoleForTesting(it) },
                onBack = { screen = Screen.Map },
                onOpenMenu = { drawerOpen = true },
            )
        }

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
                onOpenMenu = { drawerOpen = true },
                onCentreClick = { centre ->
                    evacFocusCamera = LatLng(centre.lat, centre.lon)
                    screen = Screen.Map
                },
            )
        }

        Screen.FamilyCircle -> {
            val events by mapEvents.collectAsStateWithLifecycle()
            val familyIdentity = LocalIdentity.getOrCreate(context)
            val effective = remember(events, familyIdentity.authorId) {
                effectiveCircle(events, familyIdentity.authorId)
            }
            val statuses = remember(effective, events) { circleStatuses(events, effective) }

            FamilyCircleScreen(
                modifier = modifier,
                myQrContent = remember(familyIdentity.authorId, familyIdentity.authorName) {
                    CircleCard(familyIdentity.authorId, familyIdentity.authorName).encode()
                },
                statuses = statuses,
                onCheckIn = { scope.launch { submitCheckIn(context, lat = null, lon = null) } },
                onBack = { screen = Screen.Map },
                onOpenMenu = { drawerOpen = true },
                onOpenScanner = { screen = Screen.QrScanner },
            )
        }

        Screen.QrScanner -> QrScannerScreen(
            modifier = modifier,
            onResult = { card ->
                scope.launch { submitCircleInvite(context, card.authorId, card.authorName) }
                screen = Screen.FamilyCircle
            },
            onError = { /* error is shown in the scanner screen itself */ },
            onCancel = { screen = Screen.FamilyCircle },
        )

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
                    onOpenMenu = { drawerOpen = true },
                )
            }
        }

        Screen.SosQueue -> {
            val incidents by sosViewModel.incidents.collectAsStateWithLifecycle()
            val isOfficial = role == LocalIdentity.ROLE_OFFICIAL
            SosQueueScreen(
                modifier = modifier,
                incidents = incidents,
                myLat = HomeLocationStore.get(context)?.lat,
                myLon = HomeLocationStore.get(context)?.lon,
                onAcknowledge = { sosViewModel.advance(it, SosState.ACKNOWLEDGED) },
                onEnRoute = { sosViewModel.advance(it, SosState.EN_ROUTE) },
                isOfficial = isOfficial,
                onMarkFalseAlarm = if (isOfficial) ({ sosViewModel.markFalseAlarm(it, undo = false) }) else null,
                onUndoFalseAlarm = if (isOfficial) ({ sosViewModel.markFalseAlarm(it, undo = true) }) else null,
                onBack = { screen = Screen.Map },
                onOpenMenu = { drawerOpen = true },
            )
        }

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

    val identity = LocalIdentity.getOrCreate(appContext)
    NavDrawer(
        open = drawerOpen,
        myDisplayName = displayFormOf(
            LocalIdentity.registeredFirstName(appContext),
            LocalIdentity.registeredLastName(appContext),
        ).ifBlank { identity.authorName },
        myRole = role,
        onDismiss = { drawerOpen = false },
        onOpenMap = { screen = Screen.Map },
        onOpenRoles = { screen = Screen.Roles },
        // Resumes to whatever was showing when the drawer was opened — editing your
        // profile from mid-report should not strand you back at the map once you are
        // done. Guarded against nesting: opening the drawer while already on the
        // profile screen must not wrap Screen.Profile inside its own resume target.
        onOpenProfile = {
            val target = screen
            screen = if (target is Screen.Profile) target else Screen.Profile(target)
        },
        onOpenFamily = { screen = Screen.FamilyCircle },
        onOpenEvac = { screen = Screen.EvacCentres },
        currentLanguage = language,
        onSetLanguage = { lang ->
            language = lang
            LanguagePrefs.set(appContext, lang)
        },
    )
    }
    }
}
