package com.macci.kaalerto.advisory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.i18n.tr
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * PAGASA's strip under the map header. Deliberately not a severity colour and not a map
 * marker: FR-3.3 wants official advisories beside the community reports and visibly
 * distinct from them, so it is a navy band carrying PAGASA's name, never a flood colour.
 */
private val PagasaNavy = Color(0xFF1F3A5F)

@Composable
fun AdvisoryBanner(advisories: List<AdvisoryPayload>, modifier: Modifier = Modifier) {
    if (advisories.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val first = advisories.first()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(PagasaNavy)
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.background(Color.White).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text("PAGASA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PagasaNavy)
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                first.event.ifBlank { first.headline },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    untilLabel(first.expires),
                    if (advisories.size > 1) tr("${advisories.size} abiso", "${advisories.size} advisories") else null,
                    tr("I-tap para basahin", "Tap to read"),
                ).joinToString(" · "),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
    if (open) AdvisoryDialog(advisories) { open = false }
}

/** The whole text, as PAGASA sent it — not translated, summarised or reworded (FR-3.3). */
@Composable
private fun AdvisoryDialog(advisories: List<AdvisoryPayload>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Abiso ng PAGASA", "PAGASA advisory")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                advisories.forEach { a ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(a.event.ifBlank { a.headline }, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(sentLabel(a.sent), untilLabel(a.expires)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (a.areas.isNotEmpty()) Text(a.areas.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        if (a.description.isNotBlank()) Text(a.description, style = MaterialTheme.typography.bodyMedium)
                        if (a.instruction.isNotBlank()) Text(a.instruction, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    tr(
                        "Galing mismo sa PAGASA (publicalert.pagasa.dost.gov.ph, CC BY 4.0), hindi binago. Hiwalay ito sa mga ulat ng residente.",
                        "Straight from PAGASA (publicalert.pagasa.dost.gov.ph, CC BY 4.0), unchanged. Separate from resident reports.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Isara", "Close")) } },
    )
}

private val timeFormat = DateTimeFormatter.ofPattern("MMM d, h:mm a")

private fun format(iso: String): String? = runCatching { OffsetDateTime.parse(iso).format(timeFormat) }.getOrNull()

@Composable
private fun untilLabel(expires: String): String? = format(expires)?.let { tr("hanggang $it", "until $it") }

@Composable
private fun sentLabel(sent: String): String? = format(sent)?.let { tr("inilabas $it", "issued $it") }
