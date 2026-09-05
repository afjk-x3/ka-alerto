package com.macci.kaalerto.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.macci.kaalerto.demo.DemoArea
import com.macci.kaalerto.detail.DetailSheet
import com.macci.kaalerto.evac.evacStates
import com.macci.kaalerto.evac.loadEvacCentres
import com.macci.kaalerto.geofence.HomeLocationStore
import com.macci.kaalerto.location.fetchCurrentLocation
import com.macci.kaalerto.mesh.MeshPermissions
import com.macci.kaalerto.mesh.MeshService
import com.macci.kaalerto.mesh.MeshState
import com.macci.kaalerto.net.rememberIsOnline
import com.macci.kaalerto.sos.SosColors
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
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
    onOpenRoles: (() -> Unit)? = null,
    onOpenEvac: (() -> Unit)? = null,
    onOpenOfficialStatus: ((featureRef: String) -> Unit)? = null,
    stormMode: Boolean = false,
    onToggleStormMode: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pack = remember { OfflineMapPack(context) }
    val packState by pack.state.collectAsStateWithLifecycle()
    val featureSummaries by viewModel.featureSummaries.collectAsStateWithLifecycle()
    var locatingReport by remember { mutableStateOf(false) }
    var selectedFeatureRef by remember { mutableStateOf<String?>(null) }
    var homeDraft by remember { mutableStateOf<HomeDraft?>(null) }
    var savedHome by remember { mutableStateOf(HomeLocationStore.get(context)) }
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

    LaunchedEffect(Unit) {
        // One prompt for everything still outstanding — location, notifications, and
        // day 6-7's Bluetooth/Wi-Fi set for the mesh. Asking for the mesh permissions
        // separately, later, would mean interrupting someone mid-flood to enable a
        // transport that only helps if it was already running.
        val wanted = buildList {
            addAll(LOCATION_PERMISSIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            addAll(MeshPermissions.required())
        }
        val missing = wanted.distinct().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        pack.ensureDownloaded()
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

    val isOnline by rememberIsOnline()
    val showChrome = !pickMode && homeDraft == null

    Column(modifier = modifier.fillMaxSize()) {
        // Map-Normal.dc.html's header ("Brgy. ... · synced/report status") is only
        // honest to show once the offline pack is actually ready — see PackStatusBanner.
        if (packState !is PackState.Ready) {
            PackStatusBanner(state = packState, modifier = Modifier.fillMaxWidth())
        } else if (onToggleStormMode != null) {
            MapHeader(
                isOnline = isOnline,
                reportsToday = reportsToday(featureSummaries, System.currentTimeMillis()),
                meshStatus = meshStatus,
                role = role,
                onRoleClick = onOpenRoles,
                stormMode = stormMode,
                onModeIconClick = onToggleStormMode,
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
                onLocationPicked = onLocationPicked,
                onFeatureTapped = { featureRef -> selectedFeatureRef = featureRef },
                onLongPress = if (!pickMode) {
                    { latLng -> homeDraft = HomeDraft(latLng.latitude, latLng.longitude, homeDraft?.radiusMeters ?: HomeLocationStore.DEFAULT_RADIUS_METERS.toFloat()) }
                } else {
                    null
                },
                geofenceCenter = geofenceCenter,
                geofenceRadius = geofenceRadius,
                evacStates = evacStateList,
                modifier = Modifier.fillMaxSize(),
            )

            if (showChrome) {
                MapLegend(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
            // Day 10's evacuation centres. A floating control on the map rather than a
            // third button in the header, which pushed the barangay name onto two lines
            // — and "where do I go" is a map question anyway.
            if (showChrome && onOpenEvac != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, LocalKaAlertoColors.current.borderEmphasis)
                        .clickable { onOpenEvac() },
                    contentAlignment = Alignment.Center,
                ) {
                    ShelterIcon(MaterialTheme.colorScheme.onBackground, Modifier.size(24.dp))
                }
            }
        }

        if (showChrome) {
            MapDisclaimer()
        }

        when {
            pickMode -> PickLocationBanner(
                onCancel = { onCancelPick?.invoke() },
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
                label = if (locatingReport) "Kinukuha ang lokasyon…" else "Mag-ulat",
                sosActive = sosActive,
                onSos = onStartSos?.let { start ->
                    {
                        scope.launch {
                            // Best fix available, but never a blocker: §6.1 has the
                            // request going out at t+0 with the last known position and
                            // refining afterwards. A null here still opens the hold
                            // screen at the demo centre rather than refusing.
                            val location = fetchCurrentLocation(context)
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
    if (selectedSummary != null && homeDraft == null) {
        DetailSheet(
            summary = selectedSummary,
            onDismiss = { selectedFeatureRef = null },
            onCheckInPerson = { lat, lon ->
                selectedFeatureRef = null
                onStartReportAt?.invoke(lat, lon)
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
    } else if (selectedFeatureRef != null && selectedSummary == null) {
        // The feature vanished from under the sheet (e.g. events reloaded) — don't
        // leave a sheet open with nothing to show.
        selectedFeatureRef = null
    }
}

/** Shown only while [MapScreen]'s pickMode is active — GPS's fallback path (BUILD_TASKS.md day 3). */
@Composable
private fun PickLocationBanner(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(MaterialTheme.colorScheme.inverseSurface).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Tapikin ang mapa para itakda ang lokasyon",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            Text(
                "Tap the map to set the report location",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = "Kanselahin", tint = MaterialTheme.colorScheme.inverseOnSurface)
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
                    if (sosActive) "aktibo" else "pindutin",
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
 */
@Composable
private fun PackStatusBanner(state: PackState, modifier: Modifier = Modifier) {
    val (headline: String, detail: String?) = when (state) {
        PackState.Unknown -> "Checking offline map…" to null
        PackState.Absent -> "No offline map yet" to "Starting download. This needs a connection once."
        is PackState.Downloading -> {
            val pct = state.fraction?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
            "Downloading offline map$pct" to
                "${state.completedTiles} tiles${if (!state.isPrecise) " (estimating total)" else ""}"
        }
        is PackState.Ready ->
            "Offline map ready" to "${state.tileCount} tiles · works with no signal"
        is PackState.Failed -> "Offline map failed" to state.reason
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
        if (state is PackState.Downloading) {
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
    onFeatureTapped: (String) -> Unit,
    onLongPress: ((LatLng) -> Unit)?,
    geofenceCenter: Pair<Double, Double>?,
    geofenceRadius: Double,
    evacStates: List<com.macci.kaalerto.evac.EvacState>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // onCreate must happen before onStart, and ON_CREATE may already have fired by the
    // time this composable enters, so it is called here rather than in the observer.
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }

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
    LaunchedEffect(maplibreMap, featureSummaries) {
        maplibreMap?.style?.let { updateEventMarkers(it, featureSummaries) }
    }

    LaunchedEffect(maplibreMap, evacStates) {
        maplibreMap?.style?.let { updateEvacMarkers(it, evacStates) }
    }

    LaunchedEffect(maplibreMap, geofenceCenter, geofenceRadius) {
        maplibreMap?.style?.let { updateGeofenceCircle(it, geofenceCenter, geofenceRadius) }
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

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            if (maplibreMap == null) {
                view.getMapAsync { map ->
                    map.setStyle(DemoArea.STYLE_URL) { style ->
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(DemoArea.centre, DemoArea.INITIAL_ZOOM)
                        )
                        if (showLocation) {
                            enableBlueDot(map.locationComponent, context, style)
                        }
                        maplibreMap = map
                    }
                }
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
