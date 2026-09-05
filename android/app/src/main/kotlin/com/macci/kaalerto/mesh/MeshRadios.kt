package com.macci.kaalerto.mesh

import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat

/**
 * Whether the radios the mesh actually rides on are switched on.
 *
 * This exists because `startAdvertising()` reporting success is *not* the same claim.
 * Nearby brings up whichever mediums it can and still calls back successfully when the
 * useful ones are unavailable — observed directly on the API 34 emulator, where
 * advertising succeeded with Wi-Fi LAN failing underneath it. Trusting that callback
 * alone would leave the header saying "Naghahanap ng kalapit na phone" on a phone whose
 * Bluetooth is off, which is a lie in exactly the situation the product is for.
 *
 * The location check is the one BUILD_TASKS.md days 6-7 singles out: Nearby needs
 * location *services* on, not merely the permission granted. The two are easy to
 * conflate and fail identically — silently, with no peers ever found.
 */
object MeshRadios {

    data class Readiness(val bluetoothOn: Boolean, val locationOn: Boolean) {
        val ready: Boolean get() = bluetoothOn && locationOn

        /** Null when nothing is wrong. Otherwise, what the resident has to go and switch on. */
        val message: String?
            get() = when {
                !bluetoothOn && !locationOn -> "Buksan ang Bluetooth at Location para sa mesh"
                !bluetoothOn -> "Buksan ang Bluetooth para sa mesh"
                !locationOn -> "Buksan ang Location para sa mesh"
                else -> null
            }
    }

    fun check(context: Context): Readiness {
        val bluetoothOn = context.getSystemService(BluetoothManager::class.java)
            ?.adapter
            ?.isEnabled == true
        val locationManager = context.getSystemService(LocationManager::class.java)
        val locationOn = locationManager != null && LocationManagerCompat.isLocationEnabled(locationManager)
        return Readiness(bluetoothOn = bluetoothOn, locationOn = locationOn)
    }
}
