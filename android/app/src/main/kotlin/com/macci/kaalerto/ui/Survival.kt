package com.macci.kaalerto.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Survival mode (PRD §6, NFR-3): critical battery. True black, map and SOS only, every
 * other feature shown as paused rather than hidden, and the periodic cloud sync paused.
 * The Bluetooth relay keeps running and new events still upload at once, because that is
 * how an SOS leaves the phone.
 *
 * Read outside Compose by `sync/SupabaseSyncLoop.kt`, so the state lives here.
 */
object SurvivalState {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()
    fun set(value: Boolean) { _active.value = value }
}

/** At or below this, on battery, Survival mode turns itself on. */
const val SURVIVAL_BATTERY_PERCENT = 15

/** The battery as the system reports it: percent (null until known) and whether it is charging. */
data class BatteryReading(val percent: Int?, val charging: Boolean)

fun shouldAutoSurvive(reading: BatteryReading): Boolean =
    reading.percent != null && !reading.charging && reading.percent <= SURVIVAL_BATTERY_PERCENT

/** Follows the sticky battery broadcast for as long as the caller is composed. */
@Composable
fun rememberBattery(): State<BatteryReading> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(BatteryReading(null, false)) }
    DisposableEffect(context) {
        fun read(intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            state.value = BatteryReading(
                percent = if (level >= 0 && scale > 0) level * 100 / scale else null,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            )
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = read(intent)
        }
        read(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}
