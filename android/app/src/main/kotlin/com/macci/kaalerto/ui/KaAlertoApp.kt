package com.macci.kaalerto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.identity.OnboardingScreen
import com.macci.kaalerto.map.MapScreen
import com.macci.kaalerto.nav.Screen
import com.macci.kaalerto.report.ReportScreen

/** Root screen switch — see [Screen] for why this isn't a navigation graph. */
@Composable
fun KaAlertoApp(modifier: Modifier = Modifier, stormMode: Boolean = false, onToggleStormMode: (() -> Unit)? = null) {
    val context = LocalContext.current
    // PRD §9: registration is the first screen on an unregistered device.
    var screen by remember {
        mutableStateOf<Screen>(if (LocalIdentity.isRegistered(context)) Screen.Map else Screen.Onboarding(resume = null))
    }
    val registered = LocalIdentity.isRegistered(context)

    // Viewing is never gated (see OnboardingScreen); authoring always is. Every path
    // that ends in an event carrying this person's name passes through here, and the
    // screen it was heading for rides along in `resume`, so registering picks up where
    // they left off — a report filed after registering lands at the spot GPS found.
    fun gated(destination: Screen): Screen =
        if (LocalIdentity.isRegistered(context)) destination else Screen.Onboarding(resume = destination)

    // There is no navigation back stack, so without this the system Back button on the
    // report form or the map-tap picker finishes the activity — closing the app and
    // throwing away a half-filled report. Back now does what the on-screen arrow and
    // the picker's cancel already do: return to the map. On the map itself it stays
    // unhandled, so Back leaves the app as usual. On registration it is the same as
    // "Tingnan muna ang mapa".
    BackHandler(enabled = screen != Screen.Map) { screen = Screen.Map }

    when (val current = screen) {
        Screen.Map -> MapScreen(
            modifier = modifier,
            onStartReport = { lat, lon, accuracy -> screen = gated(Screen.Report(lat, lon, accuracy)) },
            onEnterPickLocation = { screen = gated(Screen.PickLocation) },
            // Day 4's conflict sheet: "I-check ko ngayon" files a fresh report at the
            // conflicted spot rather than a confirm/dispute — see detail/DetailSheet.kt.
            onStartReportAt = { lat, lon -> screen = gated(Screen.Report(lat, lon, null)) },
            stormMode = stormMode,
            onToggleStormMode = onToggleStormMode,
            onNeedsRegistration = if (registered) null else { { screen = Screen.Onboarding(resume = null) } },
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

        is Screen.Onboarding -> {
            var firstName by remember { mutableStateOf(LocalIdentity.registeredFirstName(context)) }
            var lastName by remember { mutableStateOf(LocalIdentity.registeredLastName(context)) }
            // Blank on a first run rather than prefilled with the demo area: a guessed
            // default is one tap away from being accepted unread.
            var barangay by remember { mutableStateOf(LocalIdentity.homeBarangay(context)) }
            OnboardingScreen(
                modifier = modifier,
                firstName = firstName,
                onFirstNameChange = { firstName = it },
                lastName = lastName,
                onLastNameChange = { lastName = it },
                barangay = barangay,
                onBarangayChange = { barangay = it },
                onDone = {
                    LocalIdentity.register(context, firstName, lastName, barangay)
                    screen = current.resume ?: Screen.Map
                },
                onViewMapFirst = { screen = Screen.Map },
            )
        }
    }
}
