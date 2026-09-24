package com.macci.kaalerto.location

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

/**
 * One tap from "location unknown" to a fix: asks for the location permission if it is
 * missing, then shows Play Services' own "turn on location" dialog if location is off.
 * [onReady] runs once both are in place (or were already), so the caller can look again.
 * Only ever fired by the person tapping a button — never on its own (see PermissionPrefs).
 */
@Composable
fun rememberTurnOnLocation(onReady: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val latestOnReady = rememberUpdatedState(onReady)

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) latestOnReady.value()
    }

    fun ensureLocationOn() {
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L).build())
            .build()
        LocationServices.getSettingsClient(context).checkLocationSettings(request)
            .addOnSuccessListener { latestOnReady.value() }
            .addOnFailureListener { error ->
                if (error is ResolvableApiException) {
                    settingsLauncher.launch(IntentSenderRequest.Builder(error.resolution).build())
                }
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) ensureLocationOn()
    }

    return {
        if (hasLocationPermission(context)) {
            ensureLocationOn()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
}
