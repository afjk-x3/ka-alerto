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
 * What a coordinate resolves to.
 *
 * [barangay] is kept separate from [label] because the registration screen needs both,
 * for different jobs: the label is what a person reads to recognise where they are, and
 * the barangay is what gets *stored* against their reports. Handing back one joined
 * string would have meant re-splitting it at the call site, which is the same guessing
 * the name fields were separated to avoid.
 */
data class Place(val barangay: String?, val label: String)

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
suspend fun describePlace(context: Context, lat: Double, lon: Double): Place? {
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

    return address?.let { Place(barangay = barangayOf(it), label = readableName(it) ?: return null) }
}

/**
 * The barangay, as the geocoder understands it.
 *
 * `subLocality` is where a Philippine barangay normally lands. It is not guaranteed —
 * some places return a district or nothing at all — so this is allowed to be null and
 * the caller keeps whatever was already in the field rather than blanking it. Nothing
 * downstream treats this as authoritative: it is a pre-fill the person can correct, and
 * `submissions` still carry the coordinate as the real datum.
 */
private fun barangayOf(address: Address): String? =
    address.subLocality?.takeIf { it.isNotBlank() }

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
