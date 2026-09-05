package com.macci.kaalerto.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.macci.kaalerto.map.MapScreen
import com.macci.kaalerto.nav.Screen
import com.macci.kaalerto.report.ReportScreen

/** Root screen switch — see [Screen] for why this isn't a navigation graph. */
@Composable
fun KaAlertoApp(modifier: Modifier = Modifier) {
    var screen by remember { mutableStateOf<Screen>(Screen.Map) }

    when (val current = screen) {
        Screen.Map -> MapScreen(
            modifier = modifier,
            onStartReport = { lat, lon, accuracy -> screen = Screen.Report(lat, lon, accuracy) },
            onEnterPickLocation = { screen = Screen.PickLocation },
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
    }
}
