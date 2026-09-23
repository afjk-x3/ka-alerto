package com.macci.kaalerto.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.macci.kaalerto.identity.Perm
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
import com.macci.kaalerto.location.BundledPsgc
import com.macci.kaalerto.location.Psgc
import com.macci.kaalerto.location.describePlace
import com.macci.kaalerto.location.fetchAccurateLocation
import com.macci.kaalerto.location.fetchCurrentLocation
import org.maplibre.android.geometry.LatLng
import com.macci.kaalerto.i18n.AppLanguage
import com.macci.kaalerto.i18n.LanguagePrefs
import com.macci.kaalerto.i18n.LocalAppLanguage
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.identity.displayFormOf
import androidx.compose.runtime.rememberCoroutineScope
import com.macci.kaalerto.evac.AddShelterScreen
import com.macci.kaalerto.report.ReportsListScreen
import com.macci.kaalerto.evac.EvacScreen
import com.macci.kaalerto.evac.EvacState
import com.macci.kaalerto.evac.ShelterDraft
import com.macci.kaalerto.evac.evacStates
import com.macci.kaalerto.evac.loadEvacCentres
import com.macci.kaalerto.evac.removeEvacCentre
import com.macci.kaalerto.evac.resolveCentres
import com.macci.kaalerto.evac.submitEvacCentre
import com.macci.kaalerto.evac.submitEvacStatus
import com.macci.kaalerto.evac.suggestBarangays
import com.macci.kaalerto.evac.suggestMunicipalities
import com.macci.kaalerto.identity.RoleScreen
import com.macci.kaalerto.map.ensureHomePack
import com.macci.kaalerto.map.homeStart
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
import com.macci.kaalerto.family.MyCircleQrScreen
import com.macci.kaalerto.family.QrScannerScreen
import com.macci.kaalerto.family.circleStatuses
import com.macci.kaalerto.family.effectiveCircle
import com.macci.kaalerto.family.myLastCheckInMs
import com.macci.kaalerto.family.encode
import com.macci.kaalerto.family.submitCheckIn
import com.macci.kaalerto.family.submitCircleInvite
import kotlinx.coroutines.delay

/** Root screen switch — see [Screen] for why this isn't a navigation graph. */
@Composable
fun KaAlertoApp(
    modifier: Modifier = Modifier,
    stormMode: Boolean = false,
    onToggleStormMode: (() -> Unit)? = null,
    /** Set when the activity was opened by tapping day 9's nearby-SOS alert. */
    openSosId: String? = null,
    /** Set when it was opened by tapping the home-radius flood alert. */
    openFeatureRef: String? = null,
) {
    val appContext = LocalContext.current
    // On-demand location ask for "Subukan ulit" on the onboarding form and the profile
    // screen (23 Sep 2026: the profile screen's own permission section was removed, so
    // this is now the only way to grant location from there after declining once).
    // Fire-and-forget: granting it doesn't retry the fetch by itself, same as the map's
    // own "Nasaan ako" -- the person taps the button again once they've said yes.
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
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
    // Starts blank rather than defaulting to the demo barangay: a name that looks
    // already filled in reads as already detected, when a fresh registration with no
    // GPS fix yet has detected nothing. The geocode-follow effect below fills it once
    // a fix actually resolves; until then the field stays editable (ProfileFields.kt's
    // `editingBarangay` defaults to true exactly when this is blank).
    var draftBarangay by remember { mutableStateOf(LocalIdentity.homeBarangay(appContext)) }
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
    // Free text with suggestions. Optional for everyone; an official manages only this municipality's shelters.
    var draftMunicipality by remember { mutableStateOf(LocalIdentity.homeMunicipality(appContext)) }
    // Like the barangay: follows the detected location until somebody corrects it, then stops following.
    var municipalityCorrected by remember { mutableStateOf(LocalIdentity.homeMunicipality(appContext).isNotBlank()) }
    var municipalityFromLocation by remember { mutableStateOf(false) }
    // The bundled municipality and barangay list, for suggestions. Empty until it has loaded.
    var psgc by remember { mutableStateOf<Psgc?>(null) }
    LaunchedEffect(Unit) { psgc = BundledPsgc.get(appContext) }
    // The shelter being added lives here so it survives the trip to the map picker.
    var shelterDraft by remember { mutableStateOf(ShelterDraft()) }
    var locatingShelter by remember { mutableStateOf(false) }
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
    // Set alongside evacFocusCamera by a shelter-card tap, so the map's bottom card shows what was
    // tapped. Cleared by the card's own X, not on entry — a resident should be able to pan around
    // and come back to the same shelter without the card vanishing underneath them.
    var shelterFocus by remember { mutableStateOf<EvacState?>(null) }
    // Set when a responder taps "Nakita ko" / "Nakita ko — papunta na" on the SOS queue,
    // so the map they land on immediately shows the request's location — unlike
    // evacFocusCamera this is not cleared on entry, since the marker and banner
    // (map/MapScreen.kt's SosFocusBanner) are meant to persist until dismissed, not just
    // move the camera once.
    var sosFocus by remember { mutableStateOf<LatLng?>(null) }
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
    //
    // The first-run form counts as an untouched start too. An unregistered device — and
    // since the surname became required, every one-name registration — opens on
    // Screen.Onboarding(Screen.Map), and an active SOS outranks finishing a form
    // (PRD §9: SOS is never gated).
    var initialSosRedirectDone by remember { mutableStateOf(false) }
    LaunchedEffect(activeSos) {
        val sos = activeSos
        val untouchedStart = screen == Screen.Map || screen == Screen.Onboarding(Screen.Map)
        if (!initialSosRedirectDone && sos != null && untouchedStart) {
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
        // The municipality follows the pin the same way, and a barangay filled in for a different one would be wrong.
        val municipality = place?.municipality
        if (municipality != null && !municipalityCorrected) {
            draftMunicipality = municipality
            municipalityFromLocation = true
            // The untouched default is the demo barangay. Somewhere else it is simply wrong, so it is cleared
            // and the field offers that municipality's barangays instead of keeping a barangay of another town.
            if (resolved == null && !barangayCorrected && municipality != DemoArea.MUNICIPALITY && draftBarangay == DemoArea.BARANGAY_NAME) {
                draftBarangay = ""
                barangayFromLocation = false
            }
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

    // The feature whose sheet the registration gate interrupted, reopened on return.
    var reopenFeatureRef by remember { mutableStateOf<String?>(null) }
    // Same for the flood alert: land on that report's sheet, where Confirm lives.
    LaunchedEffect(openFeatureRef) {
        val ref = openFeatureRef ?: return@LaunchedEffect
        reopenFeatureRef = ref
        screen = Screen.Map
    }

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

    // No back stack on this branch, so without this the system Back button would finish
    // the activity from any screen. Each target is what that screen's own back/cancel
    // control already does. Null means "leave it to the system", i.e. leave the app.
    // Ported from passable-v0 (0987318).
    val backTarget: Screen? = when (val current = screen) {
        Screen.Map -> null
        // Back on the first-run gate leaves the app; it must never skip registration.
        is Screen.Onboarding -> if (LocalIdentity.isRegistered(context)) Screen.Map else null
        is Screen.Profile -> current.resume
        Screen.PickHome -> pickHomeReturn
        Screen.AddShelter -> Screen.EvacCentres
        Screen.PickShelter -> Screen.AddShelter
        is Screen.SosAddContext -> Screen.SosStatus(current.sosId)
        is Screen.SosRescueCard -> Screen.SosStatus(current.sosId)
        Screen.QrScanner, Screen.MyCircleQr, Screen.CreateCircle, Screen.JoinCircle -> Screen.FamilyCircle
        else -> Screen.Map
    }
    BackHandler(enabled = drawerOpen || backTarget != null) {
        if (drawerOpen) drawerOpen = false else backTarget?.let { screen = it }
    }

    CompositionLocalProvider(LocalAppLanguage provides language) {
    // Edge-to-edge is on (MainActivity.kt) so the OS draws status/nav bars translucent
    // over the window instead of reserving space for them — done once here, at the root
    // of every screen, rather than per screen, so nothing new can reintroduce the
    // overlap. RescueCardScreen's full-black background still paints edge to edge behind
    // the (now inset) content; only the content itself moves, not the color.
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
    when (val current = screen) {
        Screen.Map -> {
        // remember, not a live read: the map's own native init is slow enough that a plain `val` read
        // here would still be seeing the OLD (correct) value when this LaunchedEffect nulls the state
        // below, but the recomposition it triggers hands MapScreen the NEW (null) value before its
        // camera-placement effect ever fires — the jump silently reverted to the home fallback (found
        // 22 Sep testing the shelter card). remember captures once per visit to this branch instead.
        val focusCamera = remember { evacFocusCamera }
        LaunchedEffect(Unit) { if (focusCamera != null) evacFocusCamera = null }
        // A spot to open is consumed once: left set, its sheet reopened every time the map came back
        // (found while adding the reports list, which uses the same path as the flood alert).
        val reopenRef = reopenFeatureRef
        LaunchedEffect(Unit) { if (reopenRef != null) reopenFeatureRef = null }
        MapScreen(
            modifier = modifier,
            // A resident whose home is outside the demo area opens on it — the home pack
            // covers it offline. Demo phones (home inside) still get null here and open on
            // the demo area, so the scripted demo is unchanged.
            initialCamera = sosFocus ?: focusCamera ?: homeStart(HomeLocationStore.get(context))?.let { (lat, lon) -> LatLng(lat, lon) },
            sosFocus = sosFocus,
            onDismissSosFocus = { sosFocus = null },
            shelterFocus = shelterFocus,
            onDismissShelterFocus = { shelterFocus = null },
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
            onOpenReports = { screen = Screen.Reports },
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
            municipality = draftMunicipality,
            onMunicipalityChange = {
                draftMunicipality = it
                municipalityCorrected = true
                municipalityFromLocation = false
            },
            municipalitySuggestions = suggestMunicipalities(emptyList(), null, draftMunicipality, psgc),
            municipalityFromLocation = municipalityFromLocation,
            barangaySuggestions = suggestBarangays(draftMunicipality, emptyList(), null, null, draftBarangay, psgc),
            home = draftHome,
            accuracyMeters = draftAccuracy,
            placeName = draftPlaceName,
            locating = locatingHome,
            onLocate = {
                if (!Perm.LOCATION.isGranted(appContext)) {
                    locationPermissionLauncher.launch(Perm.LOCATION.permissions())
                } else {
                    scope.launch {
                        locatingHome = true
                        val fix = fetchAccurateLocation(appContext)
                        if (fix != null) {
                            draftHome = fix.latitude to fix.longitude
                            draftAccuracy = fix.accuracy
                        }
                        locatingHome = false
                    }
                }
            },
            onPickOnMap = { screen = Screen.PickHome },
            // Resuming, not just dismissing — see `gated`.
            onDone = {
                LocalIdentity.register(context, draftFirstName, draftLastName, draftPhone, draftBarangay)
                LocalIdentity.setHomeMunicipality(context, draftMunicipality)
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
                    //
                    // Rebuilt, not just adopted, when the home differs from the centre the
                    // existing pack was built for (map/HomePackStore.kt).
                    if (!DemoArea.bounds.contains(LatLng(lat, lon))) {
                        ensureHomePack(appContext, lat, lon)
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
            val profileEvents by mapEvents.collectAsStateWithLifecycle()
            val knownCentres = remember(profileEvents) { resolveCentres(loadEvacCentres(context), profileEvents) }
            ProfileScreen(
                modifier = modifier,
                firstName = draftFirstName,
                onFirstNameChange = { draftFirstName = it },
                lastName = draftLastName,
                onLastNameChange = { draftLastName = it },
                phone = draftPhone,
                onPhoneChange = { draftPhone = it },
                municipality = draftMunicipality,
                onMunicipalityChange = {
                    draftMunicipality = it
                    municipalityCorrected = true
                    municipalityFromLocation = false
                },
                municipalitySuggestions = suggestMunicipalities(knownCentres, LocalIdentity.homeMunicipality(context), draftMunicipality, psgc),
                municipalityFromLocation = municipalityFromLocation,
                barangaySuggestions = suggestBarangays(
                    draftMunicipality, knownCentres, LocalIdentity.homeMunicipality(context), LocalIdentity.homeBarangay(context), draftBarangay, psgc,
                ),
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
                    if (!Perm.LOCATION.isGranted(appContext)) {
                        locationPermissionLauncher.launch(Perm.LOCATION.permissions())
                    } else {
                        scope.launch {
                            locatingHome = true
                            val fix = fetchAccurateLocation(appContext)
                            if (fix != null) {
                                draftHome = fix.latitude to fix.longitude
                                draftAccuracy = fix.accuracy
                            }
                            locatingHome = false
                        }
                    }
                },
                onPickOnMap = { screen = Screen.PickHome },
                onSave = {
                    LocalIdentity.register(context, draftFirstName, draftLastName, draftPhone, draftBarangay)
                    LocalIdentity.setHomeMunicipality(context, draftMunicipality)
                    draftHome?.let { (lat, lon) ->
                        HomeLocationStore.set(context, lat, lon, HomeLocationStore.DEFAULT_RADIUS_METERS)
                        // A moved home gets a new pack; see map/HomePackStore.kt.
                        if (!DemoArea.bounds.contains(LatLng(lat, lon))) {
                            ensureHomePack(appContext, lat, lon)
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

        Screen.Reports -> {
            val reportSummaries by mapViewModel.featureSummaries.collectAsStateWithLifecycle()
            // The same origin rule as the shelter list: GPS when there is a fix, the saved home until then.
            var reportsOrigin by remember { mutableStateOf(HomeLocationStore.get(context)?.let { it.lat to it.lon }) }
            LaunchedEffect(Unit) { fetchCurrentLocation(context)?.let { reportsOrigin = it.latitude to it.longitude } }
            ReportsListScreen(
                modifier = modifier,
                summaries = reportSummaries,
                fromLat = reportsOrigin?.first,
                fromLon = reportsOrigin?.second,
                onOpen = { summary ->
                    evacFocusCamera = LatLng(summary.lat, summary.lon)
                    reopenFeatureRef = summary.featureRef
                    screen = Screen.Map
                },
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
                municipality = LocalIdentity.homeMunicipality(context),
                onAddShelter = {
                    shelterDraft = ShelterDraft()
                    screen = Screen.AddShelter
                },
                onRemove = { centre -> scope.launch { removeEvacCentre(context, centre) } },
                onOpenProfile = { screen = Screen.Profile(Screen.EvacCentres) },
                onUpdate = { centreId, status, occupancy ->
                    // Added shelters live in the fold, not in the bundled list.
                    val centre = states.firstOrNull { it.centre.id == centreId }?.centre ?: return@EvacScreen
                    scope.launch { submitEvacStatus(context, centre, status, occupancy) }
                },
                onBack = { screen = Screen.Map },
                onOpenMenu = { drawerOpen = true },
                onCentreClick = { centre ->
                    evacFocusCamera = LatLng(centre.lat, centre.lon)
                    shelterFocus = states.firstOrNull { it.centre.id == centre.id }
                    screen = Screen.Map
                },
            )
        }

        Screen.AddShelter -> {
            val shelterEvents by mapEvents.collectAsStateWithLifecycle()
            val knownCentres = remember(shelterEvents) { resolveCentres(loadEvacCentres(context), shelterEvents) }
            val ownMunicipality = LocalIdentity.homeMunicipality(context)
            AddShelterScreen(
                modifier = modifier,
                municipality = ownMunicipality,
                draft = shelterDraft,
                onDraftChange = { shelterDraft = it },
                barangaySuggestions = suggestBarangays(ownMunicipality, knownCentres, ownMunicipality, LocalIdentity.homeBarangay(context), shelterDraft.barangay, psgc),
                locating = locatingShelter,
                onUseMyLocation = {
                    scope.launch {
                        locatingShelter = true
                        fetchCurrentLocation(context)?.let { shelterDraft = shelterDraft.copy(lat = it.latitude, lon = it.longitude) }
                        locatingShelter = false
                    }
                },
                onPickOnMap = { screen = Screen.PickShelter },
                onSave = {
                    val d = shelterDraft
                    scope.launch {
                        val id = submitEvacCentre(context, d.name, d.kind, d.lat ?: return@launch, d.lon ?: return@launch, d.barangay, d.capacity)
                        if (id != null) {
                            shelterDraft = ShelterDraft()
                            screen = Screen.EvacCentres
                        }
                    }
                },
                onCancel = { screen = Screen.EvacCentres },
                onOpenMenu = { drawerOpen = true },
            )
        }

        Screen.PickShelter -> MapScreen(
            modifier = modifier,
            pickMode = true,
            pickSubtitle = tr("Ituturo ang lokasyon ng silungan", "This sets the shelter's location"),
            initialCamera = shelterDraft.lat?.let { lat -> shelterDraft.lon?.let { lon -> LatLng(lat, lon) } },
            onLocationPicked = { latLng ->
                shelterDraft = shelterDraft.copy(lat = latLng.latitude, lon = latLng.longitude)
                screen = Screen.AddShelter
            },
            onCancelPick = { screen = Screen.AddShelter },
        )

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
                myLastCheckInMs = remember(events, familyIdentity.authorId) {
                    myLastCheckInMs(events, familyIdentity.authorId)
                },
                statuses = statuses,
                onCheckIn = { scope.launch { submitCheckIn(context, lat = null, lon = null) } },
                onOpenMenu = { drawerOpen = true },
                onOpenScanner = { screen = Screen.QrScanner },
                onShowMyQr = { screen = Screen.MyCircleQr },
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

        Screen.MyCircleQr -> {
            val myIdentity = LocalIdentity.getOrCreate(context)
            MyCircleQrScreen(
                modifier = modifier,
                qrContent = remember(myIdentity.authorId, myIdentity.authorName) {
                    CircleCard(myIdentity.authorId, myIdentity.authorName).encode()
                },
                displayName = myIdentity.authorName,
                onBack = { screen = Screen.FamilyCircle },
                onSwitchToScan = { screen = Screen.QrScanner },
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
                    onOpenMenu = { drawerOpen = true },
                )
            }
        }

        Screen.SosQueue -> {
            val incidents by sosViewModel.incidents.collectAsStateWithLifecycle()
            val isOfficial = role == LocalIdentity.ROLE_OFFICIAL
            // Where the responder is right now, not their registered home — a Kagawad
            // out in the field needs distance from here, not from the house. One bounded
            // fetch per visit to the queue (fetchCurrentLocation's usual 6 s + last-known
            // pattern), starting from home so the list isn't empty of distances while it resolves.
            var liveLat by remember { mutableStateOf(HomeLocationStore.get(context)?.lat) }
            var liveLon by remember { mutableStateOf(HomeLocationStore.get(context)?.lon) }
            LaunchedEffect(Unit) {
                fetchCurrentLocation(context)?.let {
                    liveLat = it.latitude
                    liveLon = it.longitude
                }
            }
            // Acknowledging is also "I need to find this place" — land back on the map
            // with the exact spot marked rather than leaving the responder on a queue
            // list with no way to see where to go.
            fun focus(sosId: String) {
                incidents.firstOrNull { it.primary.sosId == sosId }?.let {
                    sosFocus = LatLng(it.primary.lat, it.primary.lon)
                }
            }
            fun focusAndAdvance(sosId: String, state: SosState) {
                focus(sosId)
                sosViewModel.advance(sosId, state)
                screen = Screen.Map
            }
            SosQueueScreen(
                modifier = modifier,
                incidents = incidents,
                myLat = liveLat,
                myLon = liveLon,
                onAcknowledge = { focusAndAdvance(it, SosState.ACKNOWLEDGED) },
                onEnRoute = { focusAndAdvance(it, SosState.EN_ROUTE) },
                isOfficial = isOfficial,
                onMarkFalseAlarm = if (isOfficial) ({ sosViewModel.markFalseAlarm(it, undo = false) }) else null,
                onUndoFalseAlarm = if (isOfficial) ({ sosViewModel.markFalseAlarm(it, undo = true) }) else null,
                onOpenMap = { incident ->
                    focus(incident.primary.sosId)
                    screen = Screen.Map
                },
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
        onOpenReports = { screen = Screen.Reports },
        currentLanguage = language,
        onSetLanguage = { lang ->
            language = lang
            LanguagePrefs.set(appContext, lang)
        },
    )
    }
    }
}
