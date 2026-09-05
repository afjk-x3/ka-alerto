package com.macci.kaalerto

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
import com.macci.kaalerto.ui.KaAlertoApp
import com.macci.kaalerto.ui.theme.KaAlertoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Manual toggle, not isSystemInDarkTheme() — Storm mode is a condition
            // (night, rain, glare) the user or barangay declares, not a phone setting
            // (docs/02-prd.md §6). State lives here, above KaAlertoTheme, since the
            // theme itself is chosen at this level.
            var stormMode by remember { mutableStateOf(false) }

            KaAlertoTheme(stormMode = stormMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KaAlertoApp(stormMode = stormMode, onToggleStormMode = { stormMode = !stormMode })
                }
            }
        }
    }
}
