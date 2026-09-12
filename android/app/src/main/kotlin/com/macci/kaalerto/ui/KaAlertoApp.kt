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
import com.macci.kaalerto.identity.IdentityFormMode
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.identity.OnboardingScreen
import com.macci.kaalerto.map.MapScreen
import com.macci.kaalerto.nav.Screen
import com.macci.kaalerto.report.ReportScreen
import com.macci.kaalerto.sos.SosScreen

/** Root screen switch — see [Screen] for why this isn't a navigation graph. */
@Composable
fun KaAlertoApp(modifier: Modifier = Modifier, stormMode: Boolean = false, onToggleStormMode: (() -> Unit)? = null) {
    val context = LocalContext.current
    // PRD §9: registration is the first screen on an unregistered device.
    var screen by remember {
        mutableStateOf<Screen>(if (LocalIdentity.isRegistered(context)) Screen.Map else Screen.Onboarding(resume = null))
    }
    val registered = LocalIdentity.isRegistered(context)

    // The form's draft lives here rather than inside the form, so a detour to SOS from the
    // registration screen does not throw away what was already typed.
    var draftFirstName by remember { mutableStateOf(LocalIdentity.registeredFirstName(context)) }
    var draftLastName by remember { mutableStateOf(LocalIdentity.registeredLastName(context)) }
    var draftBarangay by remember { mutableStateOf(LocalIdentity.homeBarangay(context)) }

    // Registration is required at first run (the only way past it is SOS), so this is a
    // second line of defence: every path that ends in an event carrying this person's
    // name goes through here, and the screen it was heading for rides along in `resume`.
    fun gated(destination: Screen): Screen =
        if (LocalIdentity.isRegistered(context)) destination else Screen.Onboarding(resume = destination)

    // There is no navigation back stack, so without this the system Back button would
    // finish the activity from any screen — closing the app and throwing away a
    // half-filled report. Null means "leave it to the system", i.e. leave the app.
    val backTarget: Screen? = when (val current = screen) {
        Screen.Map -> null
        // Back on the first-run gate leaves the app; it must never skip registration.
        is Screen.Onboarding -> if (registered) Screen.Map else null
        is Screen.Sos -> current.returnTo
        else -> Screen.Map
    }
    BackHandler(enabled = backTarget != null) { backTarget?.let { screen = it } }

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
            onSos = { screen = Screen.Sos(returnTo = Screen.Map) },
            onProfileClick = if (!registered) null else {
                {
                    // Start from what is saved, not from an edit abandoned last time.
                    draftFirstName = LocalIdentity.registeredFirstName(context)
                    draftLastName = LocalIdentity.registeredLastName(context)
                    draftBarangay = LocalIdentity.homeBarangay(context)
                    screen = Screen.Profile
                }
            },
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

        is Screen.Onboarding -> OnboardingScreen(
            modifier = modifier,
            mode = IdentityFormMode.FIRST_RUN,
            firstName = draftFirstName,
            onFirstNameChange = { draftFirstName = it },
            lastName = draftLastName,
            onLastNameChange = { draftLastName = it },
            barangay = draftBarangay,
            onBarangayChange = { draftBarangay = it },
            onDone = {
                LocalIdentity.register(context, draftFirstName, draftLastName, draftBarangay)
                screen = current.resume ?: Screen.Map
            },
            onSos = { screen = Screen.Sos(returnTo = current) },
        )

        Screen.Profile -> OnboardingScreen(
            modifier = modifier,
            mode = IdentityFormMode.EDIT,
            firstName = draftFirstName,
            onFirstNameChange = { draftFirstName = it },
            lastName = draftLastName,
            onLastNameChange = { draftLastName = it },
            barangay = draftBarangay,
            onBarangayChange = { draftBarangay = it },
            onDone = {
                LocalIdentity.register(context, draftFirstName, draftLastName, draftBarangay)
                screen = Screen.Map
            },
            onCancel = { screen = Screen.Map },
        )

        is Screen.Sos -> SosScreen(modifier = modifier, onBack = { screen = current.returnTo })
    }
}
