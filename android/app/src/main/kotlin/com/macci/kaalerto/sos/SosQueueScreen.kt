package com.macci.kaalerto.sos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.data.haversineMeters
import com.macci.kaalerto.detail.CheckIcon
import com.macci.kaalerto.detail.MeshIcon
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
import kotlin.math.roundToInt

/**
 * design/artboards/QueueVolunteer.dc.html — the responder's list.
 *
 * The tiering in the artboard's own footer is the rule this screen follows: "Rehistradong
 * volunteer: lokasyon at bilang ng tao. Ang detalyeng medikal ay hawak ng barangay
 * official." So a responder sees the exact position and the people count, and does not
 * see medical detail — which, for anything relayed in over the mesh, they could not see
 * anyway, because it was never sent (sos/SosMeshPolicy.kt).
 *
 * Two acknowledgement buttons, matching the artboard: "Nakita ko" and "Nakita ko —
 * papunta na". Both write a `sos_state` event that travels back over the mesh exactly
 * like the request came in, so the originator's screen updates with no server anywhere.
 */
@Composable
fun SosQueueScreen(
    requests: List<SosSnapshot>,
    myLat: Double?,
    myLon: Double?,
    onAcknowledge: (String) -> Unit,
    onEnRoute: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    val open = requests.filter { it.isActive }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 16.dp, end = 16.dp, top = 38.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Humihingi ng tulong",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Responder · ${com.macci.kaalerto.demo.DemoArea.BARANGAY_NAME}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier.background(SosColors.Critical).padding(horizontal = 13.dp, vertical = 7.dp),
            ) {
                Text(
                    open.size.toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = SosColors.CardBackground,
                )
            }
        }

        val viaMesh = open.count { it.arrivedByMesh }
        if (viaMesh > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.recessedSurface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeshIcon(SosColors.Mesh, Modifier.size(17.dp))
                Spacer(Modifier.size(9.dp))
                Text(
                    "$viaMesh sa mga ito ay dumating via mesh — walang internet sa pinanggalingan",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (open.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Walang humihingi ng tulong ngayon.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                open.forEach { request ->
                    RequestCard(
                        request = request,
                        distanceMeters = if (myLat != null && myLon != null) {
                            haversineMeters(myLat, myLon, request.lat, request.lon)
                        } else {
                            null
                        },
                        onAcknowledge = { onAcknowledge(request.sosId) },
                        onEnRoute = { onEnRoute(request.sosId) },
                    )
                }
                Spacer(Modifier.size(8.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SafeShieldGlyph(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Text(
                "Lokasyon at bilang ng tao. Hindi ipinapasa sa mesh ang detalyeng medikal.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.clickable(onClick = onBack).padding(8.dp)) {
                Text("Isara", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
private fun RequestCard(
    request: SosSnapshot,
    distanceMeters: Double?,
    onAcknowledge: () -> Unit,
    onEnRoute: () -> Unit,
) {
    val colors = LocalKaAlertoColors.current
    val claimed = request.state.rank >= SosState.ACKNOWLEDGED.rank
    val accent = if (claimed) colors.safeFg else SosColors.Critical

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (claimed) colors.safeBg else MaterialTheme.colorScheme.background)
            .border(1.dp, colors.border)
            .padding(start = 0.dp),
    ) {
        Row {
            Box(Modifier.size(width = 4.dp, height = 1.dp))
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, top = 10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "%.4f, %.4f".format(request.lat, request.lon),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            metaLine(request, distanceMeters),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!claimed) {
                        Box(Modifier.background(SosColors.Critical).padding(horizontal = 9.dp, vertical = 5.dp)) {
                            Text("BAGO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SosColors.CardBackground)
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, top = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    // Separate chips rather than peopleSummary(): that helper puts the
                    // count and the companions in one string for the status screen,
                    // whose column is already labelled "Tao". Reusing it here produced
                    // "5-8 - Bata - Matanda tao", with the unit stranded at the end.
                    request.context.people?.let { Chip("$it tao") }
                    request.context.companions.takeIf { it.isNotEmpty() }?.let { Chip(it.joinToString(" · ")) }
                    waterSummary(request.context)?.let { Chip(it) }
                    if (request.context.isEmpty) Chip("Walang dagdag na detalye")
                }

                if (claimed) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckIcon(accent, Modifier.size(18.dp))
                        Spacer(Modifier.size(9.dp))
                        Text(
                            claimedLabel(request),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                        )
                    }
                    // Acknowledged is not the end: the artboard's own next step is
                    // "papunta na", and a request that is seen but unattended is
                    // docs/03-architecture.md §6.5's worst failure mode.
                    if (request.state.rank < SosState.EN_ROUTE.rank) {
                        AckButton("Papunta na ako", filled = false, onClick = onEnRoute)
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(start = 13.dp, end = 13.dp, top = 11.dp, bottom = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AckButton("Nakita ko — papunta na", filled = true, onClick = onEnRoute)
                        AckButton("Nakita ko", filled = false, onClick = onAcknowledge)
                    }
                }
            }
        }
    }
}

@Composable
private fun AckButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
            .then(if (filled) Modifier else Modifier.border(1.5.dp, colors.borderEmphasis))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (filled) {
            CheckIcon(MaterialTheme.colorScheme.onPrimary, Modifier.size(19.dp))
            Spacer(Modifier.size(9.dp))
        }
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.SemiBold,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun Chip(text: String) {
    Box(
        modifier = Modifier
            .background(LocalKaAlertoColors.current.recessedSurface)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** "6 min · 340 m · 2 hops" — the artboard's own monospace meta line. */
private fun metaLine(request: SosSnapshot, distanceMeters: Double?): String = buildList {
    add(elapsedLabel(request.startedAtMs, System.currentTimeMillis()))
    distanceMeters?.let { add("${it.roundToInt()} m") }
    if (request.hopCount > 0) add("${request.hopCount} hops")
}.joinToString(" · ")

private fun claimedLabel(request: SosSnapshot): String = when {
    request.state.rank >= SosState.EN_ROUTE.rank -> "Papunta na si ${request.claimedByName ?: "isang responder"}"
    else -> "Nakita ni ${request.claimedByName ?: "isang responder"}"
}
