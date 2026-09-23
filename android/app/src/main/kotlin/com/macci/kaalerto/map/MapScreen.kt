package com.macci.kaalerto.map

import com.macci.kaalerto.identity.shouldShowPrimer
import com.macci.kaalerto.identity.missingPrimerPerms
import com.macci.kaalerto.identity.PermissionPrefs
import com.macci.kaalerto.identity.Perm
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.macci.kaalerto.data.FeatureSummary
import com.macci.kaalerto.data.haversineMeters
import com.macci.kaalerto.demo.DemoArea
import com.macci.kaalerto.demo.isInDemoArea
import com.macci.kaalerto.demo.isInOfflineCoverage
import com.macci.kaalerto.detail.DetailSheet
import com.macci.kaalerto.evac.EvacState
import com.macci.kaalerto.evac.EvacStatus
import com.macci.kaalerto.evac.evacStates
import com.macci.kaalerto.evac.formatDistance
import com.macci.kaalerto.evac.kindLabel
import com.macci.kaalerto.evac.label
import com.macci.kaalerto.evac.loadEvacCentres
import com.macci.kaalerto.geofence.HomeLocationStore
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.location.describePlace
import com.macci.kaalerto.location.fetchCurrentLocation
import com.macci.kaalerto.mesh.MeshPermissions
import com.macci.kaalerto.mesh.MeshService
import com.macci.kaalerto.mesh.MeshState
import com.macci.kaalerto.net.rememberIsOnline
import com.macci.kaalerto.route.LatLon
import com.macci.kaalerto.route.RouteOption
import com.macci.kaalerto.route.RoutesResult
import com.macci.kaalerto.route.fetchRoutes
import com.macci.kaalerto.route.floodPoints
import com.macci.kaalerto.sos.SosColors
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private data class HomeDraft(val lat: Double, val lon: Double, val radiusMeters: Float)

/**
 * @param onStartReport Reachable only in normal browsing mode. Tries GPS first and
 *   falls back to [onEnterPickLocation] on no permission/no fix — "GPS primary,
 *   map-tap fallback" (BUILD_TASKS.md day 3) — so the caller only needs to react to
 *   whichever of the two callbacks actually fires.
 * @param pickMode When true, a tap anywhere on the map calls [onLocationPicked]
 *   instead of selecting a marker; a cancel affordance calls [onCancelPick].
 * @param onStartReportAt Bubbles up when the day 4 conflict sheet's "I-check ko
 *   ngayon" is tapped — filing a fresh report is the resolution path for a
 *   conflicting feature, not a confirm/dispute (see detail/DetailSheet.kt).
 * @param onStartSos Day 8's SOS path. Uses the same GPS-first, last-known-fallback
 *   location as a report, but unlike [onStartReport] it never bounces to pick-mode:
 *   asking someone to tap their own position on a map during a rescue is not an
 *   acceptable fallback, so a coarse fix is used and its accuracy is shown instead.
 * @param stormMode / onToggleStormMode Day 5's dark-mode toggle — a manual condition
 *   the resident or barangay declares, not a system setting (docs/02-prd.md §6), so
 *   it's a button here rather than following `isSystemInDarkTheme()`.
 */
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(),
    pickMode: Boolean = false,
    onLocationPicked: ((LatLng) -> Unit)? = null,
    onCancelPick: (() -> Unit)? = null,
    onStartReport: ((lat: Double, lon: Double, accuracyMeters: Float?) -> Unit)? = null,
    onEnterPickLocation: (() -> Unit)? = null,
    onStartReportAt: ((lat: Double, lon: Double) -> Unit)? = null,
    onStartSos: ((lat: Double, lon: Double, accuracyMeters: Float?) -> Unit)? = null,
    sosActive: Boolean = false,
    role: String = com.macci.kaalerto.identity.LocalIdentity.ROLE_RESIDENT,
    onOpenEvac: (() -> Unit)? = null,
    /** Opens the "Mga ulat" list; the "Listahan" chip next to the legend. */
    onOpenReports: (() -> Unit)? = null,
    onOpenOfficialStatus: ((featureRef: String) -> Unit)? = null,
    /**
     * Day 9's rescue queue. Non-null only for a responder or an official — and without
     * it the queue has no inbound link but an incoming SOS alert, which is why switching
     * role used to change nothing visible. See [RoleActionStrip].
     */
    onOpenQueue: (() -> Unit)? = null,
    /**
     * Non-null when this device has not registered (PRD §9). The detail sheet's
     * confirm/dispute route here instead of authoring, carrying the feature so the sheet
     * can be reopened on the way back — see [focusFeatureRef].
     */
    onNeedsRegistration: ((featureRef: String) -> Unit)? = null,
    /** A feature to select on entry, so a gated action resumes where it was interrupted. */
    focusFeatureRef: String? = null,
    /** True when [pickMode] is placing a home pin rather than a report location. */
    pickingHome: Boolean = false,
    /** Replaces "This sets the report location" when the pin is for something else, such as a shelter. */
    pickSubtitle: String? = null,
    /**
     * Where the camera opens. Defaults to the frozen demo area, which is right for the
     * map itself — every fixture lives there — and wrong for pick-mode: somebody
     * confirming their own home was being shown San Nicolas regardless of where they
     * actually were, and a single tap then silently relocated them there.
     */
    initialCamera: LatLng? = null,
    /** Open requests from other people, for the strip's count. */
    openRequestCount: Int = 0,
    stormMode: Boolean = false,
    onToggleStormMode: (() -> Unit)? = null,
    /** Non-null on the Map screen only when normal chrome is showing — see [showChrome]. */
    onOpenMenu: (() -> Unit)? = null,
    /**
     * The request a responder just acknowledged — set by `ui/KaAlertoApp.kt` when
     * "Nakita ko" / "Nakita ko — papunta na" is tapped (`sos/SosQueueScreen.kt`), so the
     * map they land on already shows exactly where to go rather than an empty barangay.
     * Cleared by [onDismissSosFocus]; the marker and banner persist across camera moves
     * until then, since panning away to get bearings shouldn't lose the spot.
     */
    sosFocus: LatLng? = null,
    onDismissSosFocus: (() -> Unit)? = null,
    /** Set by a shelter-card tap ("Mga silungan"); shows the bottom card below until dismissed. */
    shelterFocus: EvacState? = null,
    onDismissShelterFocus: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pack = remember { OfflineMapPack(context) }
    val packState by pack.state.collectAsStateWithLifecycle()
    // The whole-of-Pangasinan real coverage pack (DemoArea.pangasinanBounds) — a second,
    // much larger region downloaded the same way as the curated-area one above. Not shown
    // in the header banner (that stays about the curated pack, which is what a fresh
    // install actually needs first); its readiness only feeds isCovered below.
    val pangasinanPack = remember { OfflineMapPack(context, regionName = PANGASINAN_REGION_NAME, bounds = DemoArea.pangasinanBounds) }
    val pangasinanPackState by pangasinanPack.state.collectAsStateWithLifecycle()
    val featureSummaries by viewModel.featureSummaries.collectAsStateWithLifecycle()

    // In-app routes to a report or an SOS (route/Routing.kt). Null = no panel.
    var routeUi by remember { mutableStateOf<RouteUi?>(null) }
    var routeDisclosureFor by remember { mutableStateOf<LatLon?>(null) }
    var routeJob by remember { mutableStateOf<Job?>(null) }
    fun runRoutes(target: LatLon) {
        routeJob?.cancel()
        routeJob = scope.launch {
            routeUi = RouteUi(target, RouteUi.Status.LOCATING)
            val here = fetchCurrentLocation(context)
            if (here == null) {
                routeUi = RouteUi(target, RouteUi.Status.NO_LOCATION)
                return@launch
            }
            val from = LatLon(here.latitude, here.longitude)
            routeUi = RouteUi(target, RouteUi.Status.LOADING, origin = from)
            routeUi = when (val result = fetchRoutes(from, target, floodPoints(featureSummaries))) {
                is RoutesResult.Ok -> RouteUi(target, RouteUi.Status.DONE, result.options, 0, from)
                RoutesResult.NoConnection -> RouteUi(target, RouteUi.Status.NO_CONNECTION, origin = from)
                RoutesResult.NoRoute -> RouteUi(target, RouteUi.Status.NO_ROUTE, origin = from)
                is RoutesResult.ServiceError -> RouteUi(target, RouteUi.Status.SERVICE_ERROR, origin = from)
            }
        }
    }
    fun requestRoutes(target: LatLon) {
        if (RoutePrefs.accepted(context)) runRoutes(target) else routeDisclosureFor = target
    }
    fun openInMaps(target: LatLon) {
        val uri = Uri.parse("geo:${target.lat},${target.lon}?q=${target.lat},${target.lon}")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
    var locatingReport by remember { mutableStateOf(false) }
    // Pick-mode's draft pin: a tap moves this, nothing is committed to onLocationPicked
    // until the confirm bar's "I-save" is tapped. See PickLocationBanner below.
    var pickedLatLng by remember { mutableStateOf<LatLng?>(null) }
    var locatingPick by remember { mutableStateOf(false) }
    // The SOS button had no pending state at all: it awaited a fix and, if none came,
    // simply never navigated. Observed on device as the red button doing nothing.
    var locatingSos by remember { mutableStateOf(false) }
    var selectedFeatureRef by remember { mutableStateOf<String?>(null) }
    var showBackgroundTip by remember { mutableStateOf(BackgroundTip.shouldShow(context)) }
    // Whether featureSummaries has ever actually contained selectedFeatureRef. Guards
    // the "vanished feature" auto-clear below: a freshly-picked selectedFeatureRef (the
    // registration-gate resume, or a just-submitted report's own featureRef) can easily
    // lose the race against the reducer's own Flow — the event is committed to Room, but
    // observeAll()'s re-emission and MapViewModel's derived featureSummaries haven't
    // caught up by the very next composition. Without this flag, that one-frame gap
    // looked identical to "the feature really is gone" and cleared the selection before
    // the summary ever had a chance to arrive — observed on device as a submitted
    // report's own detail sheet silently never opening.
    var everHadSelectedSummary by remember { mutableStateOf(false) }
    // Reopens the sheet somebody was in when the registration gate interrupted them.
    LaunchedEffect(focusFeatureRef) {
        if (focusFeatureRef != null) {
            selectedFeatureRef = focusFeatureRef
            everHadSelectedSummary = false
        }
    }
    var homeDraft by remember { mutableStateOf<HomeDraft?>(null) }
    // Back while adjusting a home radius cancels the draft, same as its cancel button,
    // instead of closing the app out from under it.
    BackHandler(enabled = homeDraft != null) { homeDraft = null }
    var savedHome by remember { mutableStateOf(HomeLocationStore.get(context)) }
    // Where the camera sits once it settles. Drives the offline-coverage note below. It
    // starts where the map opens, so that note is right before the first idle event arrives.
    var cameraCentre by remember {
        mutableStateOf(
            initialCamera?.let { it.latitude to it.longitude } ?: (DemoArea.CENTRE_LAT to DemoArea.CENTRE_LON),
        )
    }
    var cameraRequest by remember { mutableStateOf<CameraRequest?>(null) }
    var locatingMe by remember { mutableStateOf(false) }
    var selectedSeverities by remember { mutableStateOf(ALL_SEVERITIES.toSet()) }
    var recencyFilter by remember { mutableStateOf(RecencyFilter.ALL) }

    var hasLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var meshPermitted by remember { mutableStateOf(MeshPermissions.allGranted(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasLocation = granted.values.any { it }
        meshPermitted = MeshPermissions.allGranted(context)
    }

    // What is still missing, explained once before the system asks. It used to fire every outstanding system
    // dialog at the moment the map first appeared, one after another with no reason given, and again on later
    // visits. Now, and only if something is missing, one dialog says what and why: "Magpatuloy" asks the system
    // for exactly that set (mesh permissions included, since asking for them mid-flood would be too late to help),
    // and "Mamaya" leaves it for the profile screen. Either answer is remembered, so the map never asks by itself again.
    var primerFor by remember { mutableStateOf<List<Perm>>(emptyList()) }
    LaunchedEffect(Unit) {
        val missing = missingPrimerPerms(context)
        if (shouldShowPrimer(missing, PermissionPrefs.primerAnswered(context))) primerFor = missing
        pack.ensureDownloaded()
        pangasinanPack.ensureDownloaded()
    }
    if (primerFor.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                PermissionPrefs.markPrimerAnswered(context)
                primerFor = emptyList()
            },
            title = { Text(tr("May mga pahintulot pang kailangan", "A few permissions are still needed")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    primerFor.forEach { perm ->
                        Column {
                            Text(perm.title(), fontWeight = FontWeight.SemiBold)
                            Text(perm.reason(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        tr(
                            "Gumagana pa rin ang app kung hindi mo ito papayagan. Mababago mo ito sa profile mo.",
                            "The app still works if you decline. You can change this later in your profile.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ask = primerFor.flatMap { it.permissions().toList() }.distinct().toTypedArray()
                    PermissionPrefs.markPrimerAnswered(context)
                    primerFor = emptyList()
                    if (ask.isNotEmpty()) permissionLauncher.launch(ask)
                }) { Text(tr("Magpatuloy", "Continue")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    PermissionPrefs.markPrimerAnswered(context)
                    primerFor = emptyList()
                }) { Text(tr("Mamaya", "Later")) }
            },
        )
    }

    // The mesh runs for as long as the app does — it is a foreground service precisely
    // so it keeps reconciling with peers while this screen is off. Hence start-only:
    // leaving the map (to file a report) must not tear down the relay, and stopping it
    // is the notification's "Ihinto" action, i.e. the resident's decision.
    LaunchedEffect(meshPermitted) {
        if (meshPermitted) MeshService.start(context)
    }
    val meshStatus by MeshState.status.collectAsStateWithLifecycle()

    DisposableEffect(pack) {
        onDispose { pack.release() }
    }

    val visibleSummaries = remember(featureSummaries, selectedSeverities, recencyFilter) {
        val now = System.currentTimeMillis()
        featureSummaries.filter { summary ->
            passesFilter(summary.severity, summary.isConflicted, summary.lastEventMs, selectedSeverities, recencyFilter, now)
        }
    }

    val geofenceCenter = homeDraft?.let { it.lat to it.lon } ?: savedHome?.let { it.lat to it.lon }
    val geofenceRadius = homeDraft?.radiusMeters?.toDouble() ?: savedHome?.radiusMeters ?: 0.0
    // Day 10's centre pins. The fixture is static, the statuses are folded from the
    // same event stream the markers use, so an official's update relayed in over the
    // mesh repaints the pin with no extra plumbing.
    val allEvents by viewModel.events.collectAsStateWithLifecycle()
    val evacCentres = remember { loadEvacCentres(context) }
    val evacStateList = remember(evacCentres, allEvents, savedHome) {
        evacStates(evacCentres, allEvents, savedHome?.lat, savedHome?.lon)
    }

    // The card's distance starts at whatever the shelter list already knew (GPS-or-home) and refines
    // to a fresh fix once one arrives — the same bounded fetch reports and SOS use, not the home
    // screen's longer stream (CLAUDE.md: don't unify the two).
    var shelterFocusDistance by remember(shelterFocus?.centre?.id) { mutableStateOf(shelterFocus?.distanceMeters) }
    LaunchedEffect(shelterFocus?.centre?.id) {
        val here = shelterFocus?.let { fetchCurrentLocation(context) } ?: return@LaunchedEffect
        shelterFocusDistance = haversineMeters(here.latitude, here.longitude, shelterFocus.centre.lat, shelterFocus.centre.lon)
    }

    val isOnline by rememberIsOnline()
    // The home pack is built at registration (7 Sep) only for a home outside the demo
    // area. Tracking it here also resumes a download registration started but could not
    // finish. It counts as coverage only once its tiles are on disk, and only around the
    // centre it was actually built for (HomePackStore), never the current home: a home
    // moved since — the long-press below builds no pack — is not covered until one is
    // built around it, and the uncovered-area note must say so.
    val homeBuiltCentre = remember(savedHome) { HomePackStore.get(context) }
    // A pack built before HomePackStore existed has no recorded centre. It is still
    // tracked around the saved home so an unfinished download resumes, as before, but it
    // counts as coverage nowhere.
    val homePackTrackedCentre = homeBuiltCentre
        ?: savedHome?.takeUnless { isInOfflineCoverage(it.lat, it.lon) }?.let { it.lat to it.lon }
    val homePack = remember(homePackTrackedCentre) {
        homePackTrackedCentre?.let { (lat, lon) ->
            OfflineMapPack(context, regionName = HOME_REGION_NAME, bounds = boundsAround(lat, lon))
        }
    }
    val homePackState = rememberTrackedPackState(homePack)
    // The single "here" slot. The instance's bounds are set by replaceWith; until then
    // it only ever tracks whatever region already carries HERE_REGION_NAME.
    var hereCentre by remember { mutableStateOf(HerePackStore.get(context)) }
    val herePack = remember { OfflineMapPack(context, regionName = HERE_REGION_NAME) }
    val herePackState = rememberTrackedPackState(herePack)
    val covered = isCovered(
        cameraCentre.first,
        cameraCentre.second,
        pangasinanPackReady = pangasinanPackState is PackState.Ready,
        homePackCentre = homeBuiltCentre?.takeIf { homePackState is PackState.Ready },
        herePackCentre = hereCentre?.takeIf { herePackState is PackState.Ready },
    )
    val onDownloadHere: (() -> Unit)? = if (isOnline) {
        {
            val (lat, lon) = cameraCentre
            HerePackStore.set(context, lat, lon)
            hereCentre = lat to lon
            herePack.replaceWith(boundsAround(lat, lon))
            // Online right now, by construction — the one cheap moment to name this spot
            // for later. describePlace caches whatever the geocoder answers.
            scope.launch { describePlace(context, lat, lon) }
        }
    } else {
        null
    }
    val showChrome = !pickMode && homeDraft == null
    // Whether a bottom info panel is floating over the map right now. These panels used to
    // be plain Column siblings below the map's weighted Box, so the map itself shrank to
    // whatever height was left over -- normally fine, but stacking two at once (an SOS
    // focus card plus its own route panel, found 22 Sep 2026) squeezed the map down to an
    // unreadable sliver. They now float over the map instead (see the Box below), so the
    // floating corner controls that would otherwise sit underneath them are hidden here.
    val hasBottomPanel = sosFocus != null || shelterFocus != null || routeUi != null

    Column(modifier = modifier.fillMaxSize()) {
        // The pack banner used to *replace* the header, which took the role badge and
        // the mesh line with it — on a fresh install with no network that left the role
        // screen unreachable entirely. The header's content (connectivity, report count,
        // peers) is true whether or not tiles have downloaded, and the banner directly
        // beneath it says the map has not; stacking them is both honest and navigable.
        if (onToggleStormMode != null) {
            MapHeader(
                isOnline = isOnline,
                reportsToday = reportsToday(featureSummaries, System.currentTimeMillis()),
                meshStatus = meshStatus,
                stormMode = stormMode,
                onModeIconClick = onToggleStormMode,
                onOpenMenu = onOpenMenu ?: {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (packState !is PackState.Ready) {
            PackStatusBanner(
                state = packState,
                isOnline = isOnline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Progress for a "download here" in flight, in the same banner the demo pack uses.
        if (herePackState is PackState.Downloading || herePackState is PackState.Failed) {
            PackStatusBanner(
                state = herePackState,
                isOnline = isOnline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showChrome && !covered && herePackState !is PackState.Downloading) {
            UncoveredAreaNote(onDownloadHere = onDownloadHere, modifier = Modifier.fillMaxWidth())
        }
        if (showChrome && showBackgroundTip) {
            BackgroundTipBanner(
                onDismiss = {
                    BackgroundTip.dismiss(context)
                    showBackgroundTip = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (showChrome && onOpenQueue != null) {
            RoleActionStrip(
                role = role,
                openRequestCount = openRequestCount,
                onOpenQueue = onOpenQueue,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (showChrome) {
            FilterBar(
                selectedSeverities = selectedSeverities,
                onToggleSeverity = { severity ->
                    selectedSeverities = if (severity in selectedSeverities) {
                        selectedSeverities - severity
                    } else {
                        selectedSeverities + severity
                    }
                },
                recency = recencyFilter,
                onRecencyChange = { recencyFilter = it },
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            MapLibreMapView(
                showLocation = hasLocation,
                featureSummaries = visibleSummaries,
                pickMode = pickMode,
                // A tap only moves the draft pin now — it no longer commits and
                // navigates away on its own. The confirm bar below calls the real
                // onLocationPicked (the caller's commit callback) when "I-save" is
                // tapped. See PickLocationBanner's onSave.
                onLocationPicked = { latLng -> pickedLatLng = latLng },
                pickedLocation = pickedLatLng,
                onFeatureTapped = { featureRef -> selectedFeatureRef = featureRef },
                onLongPress = if (!pickMode) {
                    { latLng -> homeDraft = HomeDraft(latLng.latitude, latLng.longitude, homeDraft?.radiusMeters ?: HomeLocationStore.DEFAULT_RADIUS_METERS.toFloat()) }
                } else {
                    null
                },
                geofenceCenter = geofenceCenter,
                geofenceRadius = geofenceRadius,
                evacStates = evacStateList,
                stormMode = stormMode,
                initialCamera = initialCamera,
                cameraRequest = cameraRequest,
                onCameraIdle = { lat, lon -> cameraCentre = lat to lon },
                sosFocus = sosFocus,
                routes = routeUi?.takeIf { it.status == RouteUi.Status.DONE }?.options.orEmpty(),
                activeRoute = routeUi?.active ?: 0,
                routeOrigin = routeUi?.takeIf { it.status == RouteUi.Status.DONE }?.origin,
                modifier = Modifier.fillMaxSize(),
            )

            if (showChrome && !hasBottomPanel) {
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    MapLegend()
                    if (onOpenReports != null) {
                        val listLabel = tr("Listahan ng mga ulat", "List of reports")
                        Box(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .background(MaterialTheme.colorScheme.background)
                                .border(1.dp, LocalKaAlertoColors.current.borderEmphasis)
                                .clickable(onClick = onOpenReports)
                                .semantics { contentDescription = listLabel }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(tr("Listahan", "List"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
            if (showChrome && !hasBottomPanel) {
                val noGps = tr("Walang GPS ngayon", "No GPS right now")
                val needPermission = tr("Kailangan ng pahintulot sa lokasyon", "Location permission needed")
                MapCameraControls(
                    locating = locatingMe,
                    onLocateMe = {
                        if (!hasLocation) {
                            // No permission is not "no GPS", though fetchCurrentLocation
                            // returns null for both. Say which, and ask again through the
                            // existing request, whose callback updates hasLocation.
                            android.widget.Toast.makeText(context, needPermission, android.widget.Toast.LENGTH_SHORT).show()
                            permissionLauncher.launch(LOCATION_PERMISSIONS)
                        } else if (!locatingMe) {
                            locatingMe = true
                            scope.launch {
                                // Bounded at 6 s with a last-known fallback (LocationFetcher),
                                // so this label can never hang on "Hinahanap…".
                                val fix = fetchCurrentLocation(context)
                                locatingMe = false
                                if (fix != null) {
                                    cameraRequest = CameraRequest(fix.latitude, fix.longitude)
                                } else {
                                    android.widget.Toast.makeText(context, noGps, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    // Directly above the 52 dp "Silungan" control and its 12 dp margin.
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 72.dp),
                )
            }
            // Day 10's evacuation centres. A floating control on the map rather than a
            // third button in the header, which pushed the barangay name onto two lines
            // — and "where do I go" is a map question anyway.
            //
            // **Labelled, not icon-only.** It was a bare house glyph in the bottom-right
            // corner until 7 September: the exact position and the exact icon every map
            // uses for "my home" or "recentre", in an app that *has* a home-location
            // feature two gestures away on the same screen. Nobody looking for an
            // evacuation centre would have found it, and anyone looking for their home
            // radius would have landed here. One word fixes what no icon could.
            if (showChrome && !hasBottomPanel && onOpenEvac != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .height(52.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, LocalKaAlertoColors.current.borderEmphasis)
                        .clickable { onOpenEvac() }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShelterIcon(MaterialTheme.colorScheme.onBackground, Modifier.size(22.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        tr("Silungan", "Shelters"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            // Floats over the map instead of sharing the Column below it with the rest of
            // the chrome — see hasBottomPanel's own comment for why: two of these stacking
            // used to squeeze the map's weighted Box down to a sliver.
            if (hasBottomPanel) {
                Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    if (sosFocus != null) {
                        SosFocusBanner(
                            latLng = sosFocus,
                            onDismiss = { onDismissSosFocus?.invoke() },
                            onRoutes = { requestRoutes(LatLon(sosFocus.latitude, sosFocus.longitude)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (shelterFocus != null) {
                        ShelterFocusCard(
                            state = shelterFocus,
                            distanceMeters = shelterFocusDistance,
                            onDismiss = { onDismissShelterFocus?.invoke() },
                            onRoutes = { requestRoutes(LatLon(shelterFocus.centre.lat, shelterFocus.centre.lon)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    routeUi?.let { ui ->
                        RoutePanel(
                            ui = ui,
                            onPick = { i -> routeUi = ui.copy(active = i) },
                            onOpenInMaps = { openInMaps(ui.target) },
                            onClose = {
                                routeJob?.cancel()
                                routeUi = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (showChrome) {
            MapDisclaimer()
        }

        routeDisclosureFor?.let { target ->
            RouteDisclosureDialog(
                onAccept = {
                    RoutePrefs.accept(context)
                    routeDisclosureFor = null
                    runRoutes(target)
                },
                onDismiss = { routeDisclosureFor = null },
            )
        }

        when {
            pickMode -> PickLocationBanner(
                onCancel = { onCancelPick?.invoke() },
                forHome = pickingHome,
                emptySubtitle = pickSubtitle,
                pickedLatLng = pickedLatLng,
                locatingCurrent = locatingPick,
                onUseCurrentLocation = {
                    if (locatingPick) return@PickLocationBanner
                    locatingPick = true
                    scope.launch {
                        val location = fetchCurrentLocation(context)
                        locatingPick = false
                        if (location != null) {
                            pickedLatLng = LatLng(location.latitude, location.longitude)
                        }
                    }
                },
                onSave = { pickedLatLng?.let { onLocationPicked?.invoke(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            homeDraft != null -> HomeRadiusOverlay(
                radiusMeters = homeDraft!!.radiusMeters,
                onRadiusChange = { homeDraft = homeDraft!!.copy(radiusMeters = it) },
                onSave = {
                    val draft = homeDraft!!
                    HomeLocationStore.set(context, draft.lat, draft.lon, draft.radiusMeters.toDouble())
                    savedHome = HomeLocationStore.get(context)
                    homeDraft = null
                },
                onCancel = { homeDraft = null },
                modifier = Modifier.fillMaxWidth(),
            )
            onStartReport != null -> MapActionBar(
                label = if (locatingReport) tr("Kinukuha ang lokasyon…", "Getting location…") else tr("Mag-ulat", "Report"),
                sosActive = sosActive,
                locatingSos = locatingSos,
                onSos = onStartSos?.let { start ->
                    {
                        if (locatingSos) return@let
                        locatingSos = true
                        scope.launch {
                            // Best fix available, but never a blocker: §6.1 has the
                            // request going out at t+0 with the last known position and
                            // refining afterwards. A null here still opens the hold
                            // screen at the demo centre rather than refusing.
                            val location = fetchCurrentLocation(context)
                            locatingSos = false
                            start(
                                location?.latitude ?: DemoArea.centre.latitude,
                                location?.longitude ?: DemoArea.centre.longitude,
                                location?.accuracy,
                            )
                        }
                    }
                },
                onClick = {
                    if (locatingReport) return@MapActionBar
                    locatingReport = true
                    scope.launch {
                        val location = fetchCurrentLocation(context)
                        locatingReport = false
                        if (location != null) {
                            onStartReport(location.latitude, location.longitude, location.accuracy)
                        } else {
                            onEnterPickLocation?.invoke()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val selectedSummary = featureSummaries.firstOrNull { it.featureRef == selectedFeatureRef }
    if (selectedSummary != null) everHadSelectedSummary = true
    if (selectedSummary != null && homeDraft == null) {
        DetailSheet(
            summary = selectedSummary,
            onDismiss = { selectedFeatureRef = null },
            onNeedsRegistration = onNeedsRegistration?.let { needs ->
                { needs(selectedSummary.featureRef) }
            },
            onCheckInPerson = { lat, lon ->
                selectedFeatureRef = null
                onStartReportAt?.invoke(lat, lon)
            },
            onRoutes = {
                selectedFeatureRef = null
                requestRoutes(LatLon(selectedSummary.lat, selectedSummary.lon))
            },
            // Day 10: only an official sees this, and it opens the ruling screen for
            // the feature the sheet is already about.
            onOfficialStatus = if (role == com.macci.kaalerto.identity.LocalIdentity.ROLE_OFFICIAL) {
                {
                    val ref = selectedSummary.featureRef
                    selectedFeatureRef = null
                    onOpenOfficialStatus?.invoke(ref)
                }
            } else {
                null
            },
        )
    } else if (selectedFeatureRef != null && selectedSummary == null && everHadSelectedSummary) {
        // The feature vanished from under the sheet (e.g. events reloaded) — don't
        // leave a sheet open with nothing to show. Gated on everHadSelectedSummary so
        // this only fires for a summary that genuinely disappeared after being shown,
        // not for one that simply hasn't arrived yet — see that flag's own comment.
        selectedFeatureRef = null
    }
}

/**
 * Shown after a responder taps "Nakita ko" / "Nakita ko — papunta na" on the SOS queue
 * (`sos/SosQueueScreen.kt`) and lands back on the map with [MapScreen]'s `sosFocus` set —
 * the orange marker (`updateSosFocusMarker`) is where to look, this banner is how to get
 * there. "Buksan sa Maps" hands off to whatever navigation app is installed via a plain
 * `geo:` intent: this app has no routing engine of its own (day 11's route check was
 * never built), so real turn-by-turn is only ever available through that handoff, and
 * only once there is a connection to fetch a route with.
 */
@Composable
private fun SosFocusBanner(latLng: LatLng, onDismiss: () -> Unit, onRoutes: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tr("Dito ang hiling ng tulong", "The request is here"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            Text(
                "%.5f, %.5f".format(latLng.latitude, latLng.longitude),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
        Text(
            tr("Mga ruta", "Routes"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.clickable(onClick = onRoutes).padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            tr("Buksan sa Maps", "Open in Maps"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier
                .clickable {
                    val uri = Uri.parse("geo:${latLng.latitude},${latLng.longitude}?q=${latLng.latitude},${latLng.longitude}")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = tr("Isara", "Close"), tint = MaterialTheme.colorScheme.inverseOnSurface)
        }
    }
}

/**
 * Shown after a tap on a shelter card in `evac/EvacScreen.kt`'s "Mga silungan" list — the map jumps to
 * the shelter (`KaAlertoApp.kt`'s `shelterFocus`) and this card says what's there: status, where it is,
 * how full it is (if known), and the distance and walking time from here. Distance starts at the list's
 * own GPS-or-home figure and refines once a fresh fix arrives — see [MapScreen]'s `shelterFocusDistance`.
 * Same routing path as [SosFocusBanner]: OSRM through `requestRoutes`, with its own first-use disclosure.
 */
@Composable
private fun ShelterFocusCard(state: EvacState, distanceMeters: Double?, onDismiss: () -> Unit, onRoutes: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LocalKaAlertoColors.current
    val centre = state.centre
    val accent = when (state.status) {
        EvacStatus.ACCEPTING -> colors.safeFg
        EvacStatus.NEARLY_FULL -> colors.warningFg
        EvacStatus.NOT_OPEN -> MaterialTheme.colorScheme.inverseOnSurface
    }
    val where = listOfNotNull(centre.barangay, centre.municipality).joinToString(", ").ifEmpty { null }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    centre.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
                Text(
                    listOfNotNull(kindLabel(centre.kind), where).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
            Text(
                state.status.label(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = tr("Isara", "Close"), tint = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
        Text(
            buildString {
                append(distanceMeters?.let { formatDistance(it) } ?: tr("Hindi alam ang layo", "Distance unknown"))
                val fraction = state.occupancyFraction
                if (fraction != null) {
                    append(" · ${state.occupancy} / ${centre.capacityEstimate}")
                } else {
                    centre.capacityEstimate?.let { append(" · " + tr("Kapasidad $it (tantiya)", "Capacity $it (estimate)")) }
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(modifier = Modifier.padding(top = 6.dp)) {
            Text(
                tr("Mga ruta", "Routes"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.clickable(onClick = onRoutes).padding(vertical = 4.dp, horizontal = 8.dp),
            )
            Text(
                tr("Buksan sa Maps", "Open in Maps"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .clickable {
                        val uri = Uri.parse("geo:${centre.lat},${centre.lon}?q=${centre.lat},${centre.lon}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                    .padding(vertical = 4.dp, horizontal = 8.dp),
            )
        }
    }
}

/**
 * Shown only while [MapScreen]'s pickMode is active — GPS's fallback path (BUILD_TASKS.md
 * day 3). Pick-mode serves two callers now — day 3's report location and registration's
 * home pin — so it must not say "report" in both. Telling somebody setting their house
 * that they are placing a flood report is the kind of small wrongness that makes a
 * person distrust the next screen too.
 *
 * A tap only moves the draft pin (see [MapScreen]'s `pickedLatLng`) — it used to commit
 * and navigate away on the very first tap, which meant a mis-tap could only be corrected
 * by cancelling and starting over, and there was no way to review where you'd actually
 * placed the point before it was saved. Nothing is committed until "I-save ang lokasyon"
 * is tapped here.
 */
@Composable
private fun PickLocationBanner(
    onCancel: () -> Unit,
    forHome: Boolean,
    emptySubtitle: String?,
    pickedLatLng: LatLng?,
    locatingCurrent: Boolean,
    onUseCurrentLocation: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.inverseSurface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        pickedLatLng == null && forHome -> tr("Tapikin ang mapa para ituro ang bahay mo", "Tap the map to point to your home")
                        pickedLatLng == null -> tr("Tapikin ang mapa para itakda ang lokasyon", "Tap the map to set the location")
                        else -> "%.5f, %.5f".format(pickedLatLng.latitude, pickedLatLng.longitude)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
                Text(
                    when {
                        pickedLatLng == null && forHome -> tr("Ituturo ang bahay mo", "This points to your home")
                        pickedLatLng == null -> emptySubtitle ?: tr("Ituturo ang lokasyon ng ulat", "This sets the report location")
                        else -> tr("Tapikin muli para ilipat ang pin", "Tap again to move the pin")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = tr("Kanselahin", "Cancel"), tint = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.inverseOnSurface)
                    .clickable(enabled = !locatingCurrent, onClick = onUseCurrentLocation)
                    .padding(vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    if (locatingCurrent) tr("Kinukuha…", "Getting…") else tr("Kasalukuyang lokasyon", "Current location"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
            Spacer(Modifier.size(10.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(if (pickedLatLng != null) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.35f))
                    .then(if (pickedLatLng != null) Modifier.clickable(onClick = onSave) else Modifier)
                    .padding(vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr("I-save ang lokasyon", "Save the location"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inverseSurface,
                )
            }
        }
    }
}

/**
 * Map-Normal.dc.html's action bar: a full-width "Mag-ulat" bar plus the 88 dp round SOS
 * button, not a floating rounded FAB.
 *
 * The SOS button was deliberately absent until day 8 — there was no SOS flow behind it,
 * and a red button that does nothing is worse than no red button. It is here now
 * because pressing it genuinely raises a request (`sos/`), so the artboard's layout and
 * what the app can actually do have converged.
 *
 * [sosActive] flips the label to "AKTIBO", because while a request is out this button
 * is a way back to its status, not a way to start a second one.
 */
@Composable
private fun MapActionBar(
    label: String,
    onClick: () -> Unit,
    onSos: (() -> Unit)?,
    sosActive: Boolean,
    locatingSos: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick)
                .padding(vertical = 20.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.size(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        if (onSos != null) {
            Spacer(Modifier.size(14.dp))
            Column(
                modifier = Modifier
                    .size(88.dp)
                    .background(SosColors.Critical, CircleShape)
                    .clickable(onClick = onSos),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "SOS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SosColors.CardBackground,
                )
                Text(
                    when {
                        locatingSos -> tr("sandali…", "wait…")
                        sosActive -> tr("aktibo", "active")
                        else -> tr("pindutin", "press")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SosColors.CriticalText,
                )
            }
        }
    }
}

/**
 * Honest status, per the project's rule against implying a capability the app does not
 * have. A half-downloaded pack must not look like a working offline map — that is the
 * one claim that cannot break on stage.
 *
 * **A download that cannot start must not render as one that is at 0%.** On a first run
 * with no network this sat at "Downloading offline map · 0% / 0 tiles (estimating total)"
 * indefinitely, which is exactly the spinner `docs/03-architecture.md` §6.4.4 forbids —
 * and needlessly, because the app already knows connectivity is false. It now says it is
 * waiting for a connection, and says the parts that *do* work meanwhile: reports and SOS
 * never needed tiles. (The download itself is fine and resumes on its own the moment a
 * connection returns — this was only ever a copy problem.)
 */
@Composable
private fun PackStatusBanner(state: PackState, isOnline: Boolean, modifier: Modifier = Modifier) {
    val stalled = !isOnline && state is PackState.Downloading && state.completedTiles == 0L
    val (headline: String, detail: String?) = when {
        stalled -> tr("Naghihintay ng koneksyon", "Waiting for a connection") to
            tr(
                "Hindi pa na-download ang mapa. Gumagana pa rin ang pag-uulat at ang SOS.",
                "The map hasn't downloaded yet. Reporting and SOS still work.",
            )
        state is PackState.Unknown -> tr("Tinitingnan ang offline na mapa…", "Checking the offline map…") to null
        state is PackState.Absent -> tr("Wala pang offline na mapa", "No offline map yet") to
            (if (isOnline) tr("Sinisimulan ang download.", "Starting the download.") else tr("Kailangan ng koneksyon nang isang beses.", "Needs a connection once."))
        state is PackState.Downloading -> {
            val pct = state.fraction?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
            tr("Dina-download ang mapa$pct", "Downloading the map$pct") to
                "${state.completedTiles} " + tr(
                    "tile${if (!state.isPrecise) " (tinatantiya ang kabuuan)" else ""}",
                    "tile${if (state.completedTiles == 1L) "" else "s"}${if (!state.isPrecise) " (estimating the total)" else ""}",
                )
        }
        state is PackState.Ready ->
            tr("Handa na ang offline na mapa", "The offline map is ready") to
                tr("${state.tileCount} tile · gumagana kahit walang signal", "${state.tileCount} tiles · works even without signal")
        state is PackState.Failed -> tr("Hindi na-download ang mapa", "The map didn't download") to (state as PackState.Failed).reason
        else -> "" to null
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = headline,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // No bar while stalled: a progress indicator under "waiting for a connection" is
        // the same implied-activity problem as the 0%% headline it replaced.
        if (state is PackState.Downloading && !stalled) {
            val fraction = state.fraction
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * [MapView] is an Android view with a manual lifecycle, so it is bridged rather than
 * reimplemented. Missing any of these callbacks leaks the GL surface.
 */
@Composable
private fun MapLibreMapView(
    showLocation: Boolean,
    featureSummaries: List<FeatureSummary>,
    pickMode: Boolean,
    onLocationPicked: ((LatLng) -> Unit)?,
    /** The draft pin to render while [pickMode] is active; null clears it. */
    pickedLocation: LatLng? = null,
    onFeatureTapped: (String) -> Unit,
    onLongPress: ((LatLng) -> Unit)?,
    geofenceCenter: Pair<Double, Double>?,
    geofenceRadius: Double,
    evacStates: List<com.macci.kaalerto.evac.EvacState>,
    /** Day 5's declared condition. Re-tints the basemap — see map/StormMapStyle.kt. */
    stormMode: Boolean,
    /** Where the camera opens; the frozen demo area when null. */
    initialCamera: LatLng? = null,
    /** A one-shot move from the camera controls; each new value animates once. */
    cameraRequest: CameraRequest? = null,
    /** The camera centre, each time it settles after a pan, zoom or animation. */
    onCameraIdle: ((lat: Double, lon: Double) -> Unit)? = null,
    /** Where a responder just acknowledged an SOS; drawn by [updateSosFocusMarker]. */
    sosFocus: LatLng? = null,
    /** Route alternatives to draw, which one is highlighted, and where the viewer is. */
    routes: List<RouteOption> = emptyList(),
    activeRoute: Int = 0,
    routeOrigin: LatLon? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // onCreate must happen before onStart, and ON_CREATE may already have fired by the
    // time this composable enters, so it is called here rather than in the observer.
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    // Bumped on every style load. The marker effects key on it because replacing the
    // style throws away every source and layer we added, including ours — without this
    // a Storm toggle would leave the basemap re-tinted and the markers gone.
    var styleEpoch by remember { mutableIntStateOf(0) }
    var cameraPlaced by remember { mutableStateOf(false) }

    // Storm restyles the loaded style in place rather than swapping to a dark style URL,
    // because offline packs are style-scoped — see map/StormMapStyle.kt. Reloading is
    // what makes the tint reversible, and it reads from the pack, so it works offline.
    LaunchedEffect(maplibreMap, stormMode) {
        val map = maplibreMap ?: return@LaunchedEffect
        map.setStyle(DemoArea.STYLE_URL) { style ->
            applyStormTint(style, stormMode)
            if (!cameraPlaced) {
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        initialCamera ?: DemoArea.centre,
                        DemoArea.INITIAL_ZOOM,
                    ),
                )
                cameraPlaced = true
            }
            if (showLocation) {
                enableBlueDot(map.locationComponent, context, style)
            }
            styleEpoch++
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    // Markers are pushed reactively once the style is loaded, instead of inside the
    // AndroidView `update` block — that block used to call `setStyle` on every
    // recomposition, which reloaded the whole style each time the event list changed.
    LaunchedEffect(maplibreMap, styleEpoch, featureSummaries) {
        if (styleEpoch == 0) return@LaunchedEffect
        maplibreMap?.style?.let { updateEventMarkers(it, featureSummaries) }
    }

    LaunchedEffect(maplibreMap, styleEpoch, evacStates) {
        if (styleEpoch == 0) return@LaunchedEffect
        maplibreMap?.style?.let { updateEvacMarkers(it, evacStates) }
    }

    LaunchedEffect(maplibreMap, styleEpoch, geofenceCenter, geofenceRadius) {
        if (styleEpoch == 0) return@LaunchedEffect
        maplibreMap?.style?.let {
            updateGeofenceCircle(it, geofenceCenter, geofenceRadius)
            updateHomeMarker(it, geofenceCenter)
        }
    }

    LaunchedEffect(maplibreMap, styleEpoch, pickedLocation) {
        if (styleEpoch == 0) return@LaunchedEffect
        maplibreMap?.style?.let { updatePickedLocationMarker(it, pickedLocation) }
    }

    LaunchedEffect(maplibreMap, styleEpoch, sosFocus) {
        if (styleEpoch == 0) return@LaunchedEffect
        maplibreMap?.style?.let { updateSosFocusMarker(it, sosFocus) }
    }

    LaunchedEffect(maplibreMap, styleEpoch, routes, activeRoute, routeOrigin) {
        if (styleEpoch == 0) return@LaunchedEffect
        maplibreMap?.style?.let { updateRouteLayers(it, routes, activeRoute, routeOrigin) }
    }

    // Frame the whole trip when new routes arrive (not when the highlight moves).
    LaunchedEffect(maplibreMap, routes) {
        val map = maplibreMap ?: return@LaunchedEffect
        if (routes.isEmpty()) return@LaunchedEffect
        val bounds = LatLngBounds.Builder()
        routes.forEach { r -> r.coords.forEach { bounds.include(LatLng(it.second, it.first)) } }
        map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80), 700)
    }

    // Pick-mode (setting a report location) and marker selection are mutually
    // exclusive per current screen state, so only one click listener is ever live.
    DisposableEffect(maplibreMap, pickMode, onLocationPicked, onFeatureTapped) {
        val map = maplibreMap
        if (map == null) {
            onDispose { }
        } else if (pickMode && onLocationPicked != null) {
            val listener = MapLibreMap.OnMapClickListener { latLng ->
                onLocationPicked(latLng)
                true
            }
            map.addOnMapClickListener(listener)
            onDispose { map.removeOnMapClickListener(listener) }
        } else {
            val listener = MapLibreMap.OnMapClickListener { latLng ->
                val featureRef = nearestTappedFeatureRef(map, latLng)
                if (featureRef != null) {
                    onFeatureTapped(featureRef)
                    true
                } else {
                    false
                }
            }
            map.addOnMapClickListener(listener)
            onDispose { map.removeOnMapClickListener(listener) }
        }
    }

    // Long-press sets/moves the home-radius draft (day 5) — a separate gesture from
    // the click listener above, so both can be registered at once with no conflict.
    DisposableEffect(maplibreMap, onLongPress) {
        val map = maplibreMap
        val callback = onLongPress
        if (map == null || callback == null) {
            onDispose { }
        } else {
            val listener = MapLibreMap.OnMapLongClickListener { latLng ->
                callback(latLng)
                true
            }
            map.addOnMapLongClickListener(listener)
            onDispose { map.removeOnMapLongClickListener(listener) }
        }
    }

    // Read through rememberUpdatedState so the listener is registered once per map rather
    // than re-registered on every recomposition (the lambda is a new object each time).
    val latestOnCameraIdle by rememberUpdatedState(onCameraIdle)
    DisposableEffect(maplibreMap) {
        val map = maplibreMap
        if (map == null) {
            onDispose { }
        } else {
            val listener = MapLibreMap.OnCameraIdleListener {
                val target = map.cameraPosition.target ?: return@OnCameraIdleListener
                latestOnCameraIdle?.invoke(target.latitude, target.longitude)
            }
            map.addOnCameraIdleListener(listener)
            onDispose { map.removeOnCameraIdleListener(listener) }
        }
    }

    LaunchedEffect(maplibreMap, cameraRequest) {
        val map = maplibreMap ?: return@LaunchedEffect
        val request = cameraRequest ?: return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(request.lat, request.lon), DemoArea.INITIAL_ZOOM),
        )
        // A user's jump must never be undone by the opening placement: without this, a
        // tap that lands before the style finishes loading gets silently overridden the
        // moment the style-load callback above sees cameraPlaced == false.
        cameraPlaced = true
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            // The style is loaded by the LaunchedEffect above, not here: it has to be
            // reloaded when Storm toggles, and the `update` block runs on every
            // recomposition.
            if (maplibreMap == null) {
                view.getMapAsync { map -> maplibreMap = map }
            }
        },
    )
}

/** Screen-pixel tap tolerance, since exactly hitting an 8dp circle is unreliable — see below. */
private const val TAP_TOLERANCE_PX = 24f

/**
 * A raw point query against [EVENTS_LAYER_ID] frequently misses the marker entirely —
 * these circles render small, and nearby seeded reports (e.g. the conflict pair and
 * its Sotto Street neighbours) sit close enough on screen that a point-exact hit test
 * is unreasonably strict. Querying a small rect around the tap and then picking
 * whichever candidate's own coordinate is nearest the tap (rather than whatever order
 * queryRenderedFeatures happens to return) handles both problems at once.
 */
private fun nearestTappedFeatureRef(map: MapLibreMap, tapped: LatLng): String? {
    val screenPoint = map.projection.toScreenLocation(tapped)
    val rect = android.graphics.RectF(
        screenPoint.x - TAP_TOLERANCE_PX,
        screenPoint.y - TAP_TOLERANCE_PX,
        screenPoint.x + TAP_TOLERANCE_PX,
        screenPoint.y + TAP_TOLERANCE_PX,
    )
    val candidates = map.queryRenderedFeatures(rect, EVENTS_LAYER_ID)
    val nearest = candidates.minByOrNull { feature ->
        val point = feature.geometry() as? org.maplibre.geojson.Point ?: return@minByOrNull Float.MAX_VALUE
        val featureScreen = map.projection.toScreenLocation(LatLng(point.latitude(), point.longitude()))
        val dx = featureScreen.x - screenPoint.x
        val dy = featureScreen.y - screenPoint.y
        dx * dx + dy * dy
    }
    return nearest?.getStringProperty(FEATURE_REF_PROPERTY)
}

@SuppressLint("MissingPermission") // guarded by the showLocation flag at the call site
private fun enableBlueDot(
    component: org.maplibre.android.location.LocationComponent,
    context: android.content.Context,
    style: org.maplibre.android.maps.Style,
) {
    component.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style).build()
    )
    component.setLocationComponentEnabled(true)
    // Do not follow the user: the map opens on the frozen demo area, and a camera that
    // chases GPS makes the demo unrepeatable.
    component.setCameraMode(CameraMode.NONE)
    component.setRenderMode(RenderMode.NORMAL)
}

/** A roof over a doorway — the evacuation-centre entry, matching the map's evac pins. */
@Composable
private fun ShelterIcon(tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = kotlin.math.min(w, h) * 0.1f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        val roof = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.08f, h * 0.48f)
            lineTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.92f, h * 0.48f)
        }
        drawPath(roof, color = tint, style = stroke)
        drawLine(tint, androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.48f), androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.9f), stroke.width, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(tint, androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.48f), androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.9f), stroke.width, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(tint, androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.9f), androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.9f), stroke.width, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

/**
 * Tracks an existing offline pack's state for as long as this screen shows it, without
 * ever creating one. A null [pack] is [PackState.Absent].
 */
@Composable
private fun rememberTrackedPackState(pack: OfflineMapPack?): PackState {
    if (pack == null) return PackState.Absent
    LaunchedEffect(pack) { pack.adoptExisting() }
    DisposableEffect(pack) { onDispose { pack.release() } }
    val state by pack.state.collectAsStateWithLifecycle()
    return state
}
