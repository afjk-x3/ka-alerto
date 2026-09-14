package com.macci.kaalerto

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.macci.kaalerto.ui.KaAlertoApp
import com.macci.kaalerto.ui.theme.KaAlertoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 35+ forces edge-to-edge on Android 15 and later: the app draws under
        // the status and navigation bars whether it asks to or not. On the API 34 emulator
        // it did not, which is how the header and the Mag-ulat bar ended up under the
        // phone's own bars on a real Android 15+ device and nowhere else. Opting in on
        // every version makes the two behave the same, and the root below is padded by
        // the safe-drawing insets (bars, cutout, keyboard) so nothing sits under them.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Manual toggle, not isSystemInDarkTheme() — Storm mode is a condition
            // (night, rain, glare) the user or barangay declares, not a phone setting
            // (docs/02-prd.md §6). State lives here, above KaAlertoTheme, since the
            // theme itself is chosen at this level.
            var stormMode by remember { mutableStateOf(false) }

            // The bars are transparent over our own background, so their icons have to
            // follow Storm too — the default follows the phone's dark-mode setting, which
            // would leave dark icons on Storm's dark background.
            DisposableEffect(stormMode) {
                val style = if (stormMode) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }

            KaAlertoTheme(stormMode = stormMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KaAlertoApp(
                        modifier = Modifier.safeDrawingPadding(),
                        stormMode = stormMode,
                        onToggleStormMode = { stormMode = !stormMode },
                    )
                }
            }
        }
    }
}
