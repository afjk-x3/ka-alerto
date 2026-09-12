package com.macci.kaalerto.family

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.sos.QrCode
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/** QR Scanner screen using zxing-android-embedded's ScanContract.
 * The ScanContract provides a full-screen scanner with square viewfinder overlay. */
@Composable
fun QrScannerScreen(
    onResult: (CircleCard) -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scanError by remember { mutableStateOf<String?>(null) }
    val notKaAlertoQrError = tr("Hindi ito KaAlerto QR", "Not a KaAlerto QR")

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        // A null result means the camera activity finished with nothing scanned — the
        // system back gesture and the in-camera back button (KaAlertoCaptureActivity)
        // both exit this way. Previously this fell through to nothing, leaving the
        // Compose fallback below ("Starting scanner...") visible and requiring its own
        // separate Cancel tap — an extra step nobody asked for. Reads as a cancel
        // immediately instead, so backing out of the camera lands straight on Family.
        val scanned = result.contents ?: run { onCancel(); return@rememberLauncherForActivityResult }
        val card = decodeCircleCard(scanned)
        if (card == null) {
            scanError = notKaAlertoQrError
            return@rememberLauncherForActivityResult
        }
        scanError = null
        onResult(card)
    }

    // Auto-launch scanner on first composition
    LaunchedEffect(Unit) {
        scanLauncher.launch(
            ScanOptions()
                .setPrompt("Itapat sa QR")
                .setBeepEnabled(false)
                .setCaptureActivity(KaAlertoCaptureActivity::class.java)
        )
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // The scanner takes over the full screen via the activity result,
        // so this UI is shown while the scanner activity is starting
        // and when it returns (for error display)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                tr("Nagsisimula ang scanner...", "Starting scanner..."),
                fontSize = 16.sp,
                color = Color.White,
            )
            Spacer(Modifier.padding(top = 16.dp))
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(horizontal = 32.dp, vertical = 12.dp)
                    .clickable { onCancel() },
            ) {
                Text(tr("Kanselahin", "Cancel"), fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        // Error display (shown when scanner returns with error)
        scanError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(error, fontSize = 16.sp, color = LocalKaAlertoColors.current.criticalFg, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.padding(top = 16.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 32.dp, vertical = 12.dp)
                            .clickable { scanError = null },
                    ) {
                        Text(tr("Subukan muli", "Try again"), fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}