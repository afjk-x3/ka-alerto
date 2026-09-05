package com.macci.kaalerto.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.geofence.HomeLocationStore

/** BUILD_TASKS.md day 5: "Home location long-press with radius slider (drawn circle on map)" — the circle itself is map/GeofenceCircle.kt, driven by the same radius value shown here. */
@Composable
fun HomeRadiusOverlay(
    radiusMeters: Float,
    onRadiusChange: (Float) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Itakda ang tahanan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Home location · ${radiusMeters.toInt()} m radius",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = radiusMeters,
                onValueChange = onRadiusChange,
                valueRange = HomeLocationStore.MIN_RADIUS_METERS.toFloat()..HomeLocationStore.MAX_RADIUS_METERS.toFloat(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Kanselahin") }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Itakda") }
            }
        }
    }
}
