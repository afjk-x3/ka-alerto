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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long to wait for a *fresh* fix before falling back to the last known one.
 *
 * `getCurrentLocation` forces a new fix and will happily wait a long time for one — that
 * is the right trade for a survey app and the wrong one here. Six seconds is roughly the
 * limit of what someone watching "Kinukuha ang lokasyon…" will read as "working" rather
 * than "broken".
 */
private const val FRESH_FIX_TIMEOUT_MS = 6_000L

/** Anything older than this is not worth treating as "where I am". */
private const val LAST_KNOWN_MAX_AGE_MS = 10L * 60 * 1000

/**
 * One-shot "where am I right now" for reports and confirm/dispute — not continuous tracking.
 *
 * **GPS first, last known second, null third**, and it is bounded at every step. That
 * ordering was always the documented intent; until 6 September 2026 the code only did the
 * first part. `getCurrentLocation(PRIORITY_HIGH_ACCURACY)` was awaited with no deadline
 * and no fallback, so on a phone with no fresh lock neither listener ever fired, the
 * coroutine suspended forever, and the caller never resumed: "Mag-ulat" sat on
 * "Kinukuha ang lokasyon…" for good, and a confirm or dispute was never written — on
 * exactly the phone least likely to hold a lock: indoors, under a roof, in a storm.
 *
 * Returns null on missing permission, on no fix within the budget with nothing recent
 * cached, and on failure. It does not throw in any of those cases: for a report, null is
 * the map-tap fallback's cue (BUILD_TASKS.md day 3); for a confirm or dispute, the caller
 * records it with no usable position, which the reducer weights as remote.
 */
@SuppressLint("MissingPermission") // guarded by the explicit permission check below
suspend fun fetchCurrentLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null

    val client = LocationServices.getFusedLocationProviderClient(context)

    return firstUsableFix<Location>(
        fresh = {
            val cancellationTokenSource = CancellationTokenSource()
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cancellationTokenSource.cancel() }
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                    .addOnSuccessListener { location -> continuation.resume(location) }
                    .addOnFailureListener { continuation.resume(null) }
            }
        },
        // The fallback the doc comments have promised since day 3. A fix from four minutes
        // ago is a far better answer than none — the water moved, the house did not.
        lastKnown = {
            suspendCancellableCoroutine { continuation ->
                client.lastLocation
                    .addOnSuccessListener { location -> continuation.resume(location) }
                    .addOnFailureListener { continuation.resume(null) }
            }
        },
        isRecent = { System.currentTimeMillis() - it.time in 0..LAST_KNOWN_MAX_AGE_MS },
    )
}

/**
 * The bounded policy itself, kept free of Play Services so it can be unit-tested (see
 * FirstUsableFixTest): a fresh fix if one arrives within [timeoutMs], else a last known one
 * that [isRecent] accepts, else null. Neither source can hold the caller past its own
 * timeout — on an emulator the fused provider always has a cached fix, so the no-lock case
 * that used to hang cannot be reproduced there, only here.
 */
internal suspend fun <T : Any> firstUsableFix(
    fresh: suspend () -> T?,
    lastKnown: suspend () -> T?,
    isRecent: (T) -> Boolean,
    timeoutMs: Long = FRESH_FIX_TIMEOUT_MS,
): T? {
    withTimeoutOrNull(timeoutMs) { fresh() }?.let { return it }
    return withTimeoutOrNull(timeoutMs) { lastKnown() }?.takeIf(isRecent)
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
