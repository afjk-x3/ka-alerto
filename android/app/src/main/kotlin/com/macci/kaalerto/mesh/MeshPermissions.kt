package com.macci.kaalerto.mesh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The runtime permissions Nearby Connections needs on `Strategy.P2P_CLUSTER`, which
 * vary by API level more than any other permission set in this app.
 *
 * `ACCESS_FINE_LOCATION` is on the list at *every* level, not just below 12: Nearby's
 * Wi-Fi legs still require it unless `BLUETOOTH_SCAN` is declared
 * `neverForLocation`, and this app genuinely does derive location from the same
 * permission for reporting, so there is nothing to gain by splitting them.
 *
 * BUILD_TASKS.md days 6-7 flag the thing this file *cannot* fix: Nearby needs location
 * **services** switched on, not merely the permission granted, and airplane mode turns
 * Bluetooth and Wi-Fi off — both must be re-enabled by hand afterwards. A granted
 * permission with the radios off looks identical to a working mesh with no peers
 * nearby, which is why [MeshService] surfaces an explicit failure rather than sitting
 * at "0 peers".
 */
object MeshPermissions {
    fun required(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    fun missing(context: Context): List<String> = required().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    fun allGranted(context: Context): Boolean = missing(context).isEmpty()
}
