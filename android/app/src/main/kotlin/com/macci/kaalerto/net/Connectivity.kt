package com.macci.kaalerto.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService

/**
 * Real network state for the map header's status row (map/MapHeader.kt) — the artboards'
 * "Synced just now" / "Walang signal" copy implies a sync pipeline that isn't wired into
 * the app yet (that's build day 13's job), so this reports plain connectivity honestly
 * rather than a sync claim the app can't back up.
 */
@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val connectivityManager = remember(context) { context.getSystemService<ConnectivityManager>() }
    val isOnline = remember { mutableStateOf(currentlyOnline(connectivityManager)) }

    androidx.compose.runtime.DisposableEffect(connectivityManager) {
        if (connectivityManager == null) {
            onDispose { }
        } else {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    isOnline.value = true
                }

                override fun onLost(network: Network) {
                    isOnline.value = currentlyOnline(connectivityManager)
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    isOnline.value = currentlyOnline(connectivityManager)
                }
            }
            connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
            onDispose { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }

    return isOnline
}

private fun currentlyOnline(connectivityManager: ConnectivityManager?): Boolean {
    val network = connectivityManager?.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
