package com.macci.kaalerto.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
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
 * limit of what someone holding a red SOS button will read as "working" rather than
 * "broken", and it is well short of the 30 s at which the SOS state machine gives up on
 * every channel and raises the rescue card.
 */
private const val FRESH_FIX_TIMEOUT_MS = 6_000L

/**
 * Anything older than this is not worth showing as "where I am". Ten minutes is the same
 * window `SosAlertWatcher.FRESH_ON_STARTUP_MS` uses for "this emergency is still live".
 */
private const val LAST_KNOWN_MAX_AGE_MS = 10L * 60 * 1000

/**
 * One-shot "where am I right now" for report and SOS authoring — not continuous tracking.
 *
 * **GPS first, last known second, null third**, and it is bounded at every step. That
 * ordering was always the documented intent; until 6 September 2026 the code only did the
 * first part. `getCurrentLocation(PRIORITY_HIGH_ACCURACY)` was awaited with no deadline
 * and no fallback, so on a phone with no fresh lock neither listener ever fired, the
 * coroutine suspended forever, and the caller never resumed — observed on device as the
 * red SOS button doing *nothing at all*, twice, with no spinner and no error. That is the
 * product's headline claim failing silently on exactly the phone least likely to hold a
 * lock: indoors, under a roof, in a storm.
 *
 * Returns null on missing permission, on no fix within the budget with nothing recent
 * cached, and on failure. It does not throw in any of those cases: for a report, null is
 * the map-tap fallback's cue (BUILD_TASKS.md day 3); for an SOS, the caller sends at the
 * demo-area centre rather than refusing, because a request with a rough position beats no
 * request at all (`docs/03-architecture.md` §6.1).
 */
@SuppressLint("MissingPermission") // guarded by the explicit permission check below
suspend fun fetchCurrentLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null

    val client = LocationServices.getFusedLocationProviderClient(context)

    val fresh = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) {
        val cancellationTokenSource = CancellationTokenSource()
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancellationTokenSource.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { location -> continuation.resume(location) }
                .addOnFailureListener { continuation.resume(null) }
        }
    }
    if (fresh != null) return fresh

    // The fallback the doc comments have promised since day 3. A fix from four minutes
    // ago is a far better answer than none — the water moved, the house did not.
    val lastKnown = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) {
        suspendCancellableCoroutine<Location?> { continuation ->
            client.lastLocation
                .addOnSuccessListener { location -> continuation.resume(location) }
                .addOnFailureListener { continuation.resume(null) }
        }
    }
    return lastKnown?.takeIf {
        val age = System.currentTimeMillis() - it.time
        age in 0..LAST_KNOWN_MAX_AGE_MS
    }
}


/**
 * How long registration is allowed to keep improving the home pin.
 *
 * Deliberately far longer than [FRESH_FIX_TIMEOUT_MS], because the two calls ask
 * different questions. An SOS needs *a* position now and refines afterwards; setting a
 * home happens once and is then used to decide, for months, whether a flood is near
 * enough to wake somebody. A first GPS fix indoors commonly lands at ±50-100 m and
 * tightens over the following seconds, so accepting the first one would quietly put a
 * home up to a block from the house.
 */
private const val ACCURATE_FIX_WINDOW_MS = 15_000L

/**
 * Good enough to stop waiting. Roughly a house and its yard, which is the precision the
 * 100-1000 m home radius can actually act on — holding somebody on a setup screen for
 * digits nothing reads would be spending their time for nothing.
 */
private const val GOOD_ENOUGH_ACCURACY_M = 20f

/**
 * The most accurate fix obtainable within [ACCURATE_FIX_WINDOW_MS] — for setting a home,
 * not for an emergency.
 *
 * Streams updates rather than taking one shot, keeps the tightest accuracy seen, and
 * returns early once a fix is [GOOD_ENOUGH_ACCURACY_M] or better. `getCurrentLocation`
 * hands back whichever fix arrives first, which on a cold start indoors is the *worst*
 * of the series — the exact case this screen is most likely to be used in.
 *
 * Degrades rather than fails: the best seen so far if the window expires, then
 * [fetchCurrentLocation]'s one-shot-then-last-known, then null.
 */
@SuppressLint("MissingPermission") // guarded by the permission check below
suspend fun fetchAccurateLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null

    val client = LocationServices.getFusedLocationProviderClient(context)
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
        .setMinUpdateIntervalMillis(500L)
        .setWaitForAccurateLocation(true)
        .build()

    var bestSoFar: Location? = null
    var registered: LocationCallback? = null

    val settled = try {
        withTimeoutOrNull(ACCURATE_FIX_WINDOW_MS) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        val fix = result.lastLocation ?: return
                        val best = bestSoFar
                        if (best == null || fix.accuracy < best.accuracy) bestSoFar = fix
                        if (fix.accuracy <= GOOD_ENOUGH_ACCURACY_M && continuation.isActive) {
                            continuation.resume(bestSoFar)
                        }
                    }
                }
                registered = callback
                continuation.invokeOnCancellation { client.removeLocationUpdates(callback) }
                client.requestLocationUpdates(request, callback, context.mainLooper)
            }
        }
    } finally {
        // The window expiring is the normal exit here, not an error — but the callback
        // must come off either way, or the GPS keeps running behind a screen nobody is
        // looking at, on a phone this app assumes is already low on battery.
        registered?.let { client.removeLocationUpdates(it) }
    }

    return settled ?: bestSoFar ?: fetchCurrentLocation(context)
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
