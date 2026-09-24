package com.macci.kaalerto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.macci.kaalerto.notification.EXTRA_FEATURE_REF
import com.macci.kaalerto.sos.EXTRA_SOS_ID
import com.macci.kaalerto.ui.KaAlertoApp
import com.macci.kaalerto.ui.theme.KaAlertoTheme

class MainActivity : ComponentActivity() {

    /**
     * Set when the activity was launched from day 9's nearby-SOS alert. Kept as state so
     * a second alert arriving while the app is already open still routes: onNewIntent
     * fires rather than onCreate, and without this the tap would do nothing.
     */
    private var openSosId by mutableStateOf<String?>(null)

    /** Same idea for the home-radius flood alert: the feature whose detail sheet to open. */
    private var openFeatureRef by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openSosId = intent.getStringExtra(EXTRA_SOS_ID)
        openFeatureRef = intent.getStringExtra(EXTRA_FEATURE_REF)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openSosId = intent?.getStringExtra(EXTRA_SOS_ID)
        openFeatureRef = intent?.getStringExtra(EXTRA_FEATURE_REF)
        // Edge-to-edge (status/nav bars stay visible, drawn translucent over the app) is
        // enforced by the platform on API 35+ regardless of this call — targetSdk here is
        // 37. Content itself must not sit under the bars, though: that's handled once, at
        // the root of the Compose tree (KaAlertoApp.kt's outer Box), not per screen.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // Manual toggle, not isSystemInDarkTheme() — Storm mode is a condition
            // (night, rain, glare) the user or barangay declares, not a phone setting
            // (docs/02-prd.md §6). State lives here, above KaAlertoTheme, since the
            // theme itself is chosen at this level.
            var stormMode by remember { mutableStateOf(false) }

            // Survival mode: automatic at low battery, and a switch that overrides it. The
            // override clears whenever the automatic answer changes, so turning it off at
            // 14% holds until the phone charges, not forever.
            val battery by com.macci.kaalerto.ui.rememberBattery()
            val autoSurvival = com.macci.kaalerto.ui.shouldAutoSurvive(battery)
            var survivalOverride by remember { mutableStateOf<Boolean?>(null) }
            androidx.compose.runtime.LaunchedEffect(autoSurvival) { survivalOverride = null }
            val survivalMode = survivalOverride ?: autoSurvival
            androidx.compose.runtime.SideEffect { com.macci.kaalerto.ui.SurvivalState.set(survivalMode) }

            KaAlertoTheme(stormMode = stormMode, survivalMode = survivalMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KaAlertoApp(
                        stormMode = stormMode,
                        onToggleStormMode = { stormMode = !stormMode },
                        survivalMode = survivalMode,
                        batteryPercent = battery.percent,
                        onSetSurvivalMode = { survivalOverride = it },
                        openSosId = openSosId,
                        openFeatureRef = openFeatureRef,
                    )
                }
            }
        }
    }

}
