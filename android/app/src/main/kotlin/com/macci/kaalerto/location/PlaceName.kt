package com.macci.kaalerto.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * A readable name for a coordinate — "Quiapo, Manila" — for the registration screen.
 *
 * **Best-effort, and null is a completely normal answer.** `Geocoder` on almost every
 * device is a thin client over a network service, so this returns nothing offline. That
 * is why the coordinate, not the name, is what the app stores and acts on: the pin is
 * the datum and this is a courtesy label above it. A screen that showed only the name
 * would be a screen that shows nothing in a flood.
 *
 * Registration is the one moment where a network is *likely* — somebody has just
 * installed the app — which is the same reason the artboard puts the map download here.
 * So the name is worth asking for here and nowhere else.
 */
suspend fun describePlace(context: Context, lat: Double, lon: Double): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale("fil", "PH"))

    val address: Address? = withTimeoutOrNull(PLACE_NAME_TIMEOUT_MS) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resume(null)
                    }
                })
            }
        } else {
            // Pre-33 the call is synchronous and throws on any network trouble, which
            // offline is the expected case rather than an exceptional one.
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
            }
        }
    }

    return address?.let(::readableName)
}

/**
 * Two levels, nearest first: "Quiapo, Manila". Deliberately not the full postal address —
 * this sits directly above the coordinates on a setup screen, and a street number would
 * be both wrong (it describes a GPS fix, not a doorstep) and more than anyone needs to
 * recognise where they are.
 */
private fun readableName(address: Address): String? {
    val near = address.subLocality ?: address.locality ?: address.subAdminArea
    val wider = address.locality?.takeIf { it != near }
        ?: address.subAdminArea?.takeIf { it != near }
        ?: address.adminArea?.takeIf { it != near }
    return when {
        near != null && wider != null -> "$near, $wider"
        near != null -> near
        else -> address.adminArea
    }
}

/**
 * Short on purpose. This is a label; the screen is already usable without it, so it must
 * never be the reason somebody waits.
 */
private const val PLACE_NAME_TIMEOUT_MS = 5_000L
