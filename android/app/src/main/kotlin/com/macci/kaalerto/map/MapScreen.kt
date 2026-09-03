package com.macci.kaalerto.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.macci.kaalerto.demo.DemoArea
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pack = remember { OfflineMapPack(context) }
    val packState by pack.state.collectAsStateWithLifecycle()

    var hasLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasLocation = granted.values.any { it }
    }

    LaunchedEffect(Unit) {
        if (!hasLocation) permissionLauncher.launch(LOCATION_PERMISSIONS)
        pack.ensureDownloaded()
    }

    DisposableEffect(pack) {
        onDispose { pack.release() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MapLibreMapView(
            showLocation = hasLocation,
            modifier = Modifier.fillMaxSize(),
        )
        PackStatusBanner(
            state = packState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )
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
private fun MapLibreMapView(showLocation: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // onCreate must happen before onStart, and ON_CREATE may already have fired by the
    // time this composable enters, so it is called here rather than in the observer.
    val mapView = remember { MapView(context).apply { onCreate(null) } }

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

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.getMapAsync { map ->
                map.setStyle(DemoArea.STYLE_URL) { style ->
                    map.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(DemoArea.centre, DemoArea.INITIAL_ZOOM)
                    )
                    if (showLocation) {
                        enableBlueDot(map.locationComponent, context, style)
                    }
                }
            }
        },
    )
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
