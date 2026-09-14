package com.macci.kaalerto.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.location.PlaceLookup
import com.macci.kaalerto.location.rememberPlaceName
import com.macci.kaalerto.sos.coord

/**
 * Shown when the phone's GPS puts it outside [DemoArea] — any tester not standing in San
 * Nicolas. The map opens on the demo area and never follows GPS (MapScreen's enableBlueDot:
 * a camera that chases the phone makes the demo unrepeatable), so without this the blue
 * dot sits hundreds of kilometres off-screen, and a report filed "here" lands where the
 * offline map has no tiles and nobody will look. This says what the app read and why it
 * still shows San Nicolas.
 *
 * @param onPickOnDemoMap / onUseMyLocation Non-null in the report flow, where there is a
 *   real choice: file the report at a spot picked on the demo map, or at the phone's
 *   actual location anyway. Without them the dialog is a notice with a single "Sige".
 */
@Composable
fun OutsideDemoAreaDialog(
    lat: Double,
    lon: Double,
    onDismiss: () -> Unit,
    onPickOnDemoMap: (() -> Unit)? = null,
    onUseMyLocation: (() -> Unit)? = null,
) {
    val lookup = rememberPlaceName(lat, lon, allowNetwork = true)
    val placeLine = when (lookup) {
        is PlaceLookup.Found -> lookup.place.oneLine
        PlaceLookup.Looking -> "Hinahanap ang pangalan ng lugar…"
        PlaceLookup.Unknown -> "Hindi mahanap ang pangalan ng lugar"
    }
    val markerPlace = (lookup as? PlaceLookup.Found)?.place?.oneLine ?: "labas ng demo area"
    val isReportFlow = onPickOnDemoMap != null && onUseMyLocation != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nasa labas ka ng demo area") },
        text = {
            Column {
                Text("Nabasa ng app ang lokasyon mo:", style = MaterialTheme.typography.bodyMedium)
                Text(placeLine, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${coord(lat)}, ${coord(lon)} · mga ${kmFromDemoArea(lat, lon)} km mula sa demo area",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Para sa demo at testing, laging nakatutok ang mapa sa ${DemoArea.BARANGAY_NAME}, " +
                        "${DemoArea.MUNICIPALITY}. Doon lang naka-download ang offline na mapa at ang mga " +
                        "halimbawang ulat.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Kaya kahit nasa $markerPlace ang marker mo (ang asul na tuldok), ibabalik pa rin ng app " +
                        "ang mapa sa demo site.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (isReportFlow) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Kung gagamitin mo ang lokasyon mo, doon mapupunta ang ulat — malayo sa demo map, kaya " +
                            "hindi mo ito makikita roon. Para makita ito sa mapa, pumili ng lugar sa demo map.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            if (isReportFlow) {
                TextButton(onClick = onPickOnDemoMap!!) { Text("Pumili sa demo map") }
            } else {
                TextButton(onClick = onDismiss) { Text("Sige") }
            }
        },
        dismissButton = if (isReportFlow) {
            { TextButton(onClick = onUseMyLocation!!) { Text("Gamitin ang lokasyon ko") } }
        } else {
            null
        },
    )
}
