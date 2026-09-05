package com.macci.kaalerto.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * One-shot "where am I right now" for report authoring — not continuous tracking.
 *
 * Returns null on missing permission or no fix, and does not throw either way: both
 * are the map-tap fallback's cue to take over (BUILD_TASKS.md day 3 — "GPS primary,
 * map-tap fallback"), not error conditions the caller needs to branch on separately.
 */
@SuppressLint("MissingPermission") // guarded by the explicit permission check below
suspend fun fetchCurrentLocation(context: Context): Location? {
    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    if (!hasPermission) return null

    val client = LocationServices.getFusedLocationProviderClient(context)
    val cancellationTokenSource = CancellationTokenSource()

    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancellationTokenSource.cancel() }
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
            .addOnSuccessListener { location -> continuation.resume(location) }
            .addOnFailureListener { continuation.resume(null) }
    }
}
