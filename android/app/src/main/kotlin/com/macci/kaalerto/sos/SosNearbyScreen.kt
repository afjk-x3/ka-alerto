package com.macci.kaalerto.sos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.demo.DemoArea
import com.macci.kaalerto.detail.MeshIcon
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/**
 * design/artboards/SOSNearby.dc.html — what a plain resident sees when a neighbour asks
 * for rescue.
 *
 * Everything about this screen is an exercise in *not* showing things. The artboard's
 * own copy is the specification: "Hindi ipinapakita ang eksaktong lokasyon o kung sino
 * sila." So there is no pin, no name, no medical detail — a deliberately fuzzy circle
 * and a distance rounded to 50 m, because a resident's job here is to know someone
 * nearby needs help, not to be able to walk to their door.
 *
 * One line of the artboard's copy is **changed on purpose.** It reads "Dinadala rin ito
 * ng phone mo papunta sa iba — hindi mo ito kayang basahin", which describes
 * `docs/03-architecture.md` §6.5's encrypted relay payload. This build has no crypto, so
 * instead of the payload being unreadable, the sensitive parts of it are never sent
 * (sos/SosMeshPolicy.kt). The replacement line says that, because claiming a device
 * cannot read something it simply was not given would be a lie about a privacy
 * guarantee — the worst kind to ship.
 */
@Composable
fun SosNearbyScreen(
    snapshot: SosSnapshot,
    distanceMeters: Double?,
    isResponder: Boolean,
    onBecomeResponder: () -> Unit,
    onOpenQueue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    val ageLabel = elapsedLabel(snapshot.startedAtMs, System.currentTimeMillis())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 44.dp, bottom = 12.dp),
        ) {
            Text(
                "May humihingi ng tulong",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Malapit sa iyo · $ageLabel ang nakaraan",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))

        CoarseAreaMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(colors.recessedSurface),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.canvas)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, colors.border)
                    .padding(14.dp),
            ) {
                Text(
                    distanceMeters
                        ?.let { "Humigit-kumulang ${SosAlertNotifier.roundDistance(it)} m ang layo" }
                        ?: "Malapit sa iyo",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "${DemoArea.BARANGAY_NAME}. Hindi ipinapakita ang eksaktong lokasyon o kung sino sila.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            InfoStrip(
                background = colors.safeBg,
                foreground = colors.safeFg,
                text = "Dala rin ito ng phone mo papunta sa iba.",
            )
            InfoStrip(
                background = MaterialTheme.colorScheme.background,
                foreground = MaterialTheme.colorScheme.onSurfaceVariant,
                text = "Hindi kasama sa ipinapasa ang pangalan nila o ang detalyeng medikal — hindi iyon umaalis sa phone nila.",
                bordered = true,
            )
        }

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (isResponder) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onOpenQueue),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Buksan ang listahan ng tulong",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.onBackground)
                        .clickable(onClick = onBecomeResponder),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Magparehistro bilang responder",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                // The artboard's subtext promises barangay approval. There is no
                // barangay side in this build, so this says what the button really does
                // rather than implying an approval step that does not exist.
                Text(
                    "Demo lang: sa totoong app, ang barangay ang nag-a-aktibo nito. Dito, agad kang magiging responder at makikita mo ang eksaktong lokasyon at bilang ng tao.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("Bumalik sa mapa", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoStrip(background: Color, foreground: Color, text: String, bordered: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .then(if (bordered) Modifier.border(1.dp, LocalKaAlertoColors.current.border) else Modifier)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeshIcon(foreground, Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Text(text, fontSize = 13.sp, color = foreground)
    }
}

/**
 * The artboard's "humigit-kumulang dito" area: two concentric translucent circles with a
 * dashed edge, and no pin anywhere. A pin would be a false precision — the whole point
 * is that a resident is shown a region, never a point.
 */
@Composable
private fun CoarseAreaMap(modifier: Modifier = Modifier) {
    val critical = SosColors.Critical
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centre = Offset(size.width * 0.54f, size.height * 0.46f)
            val outer = size.minDimension * 0.38f
            drawCircle(critical.copy(alpha = 0.13f), radius = outer, center = centre)
            drawCircle(
                color = critical,
                radius = outer,
                center = centre,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx())),
                ),
            )
            drawCircle(critical.copy(alpha = 0.10f), radius = outer * 0.58f, center = centre)
        }
        Text(
            "humigit-kumulang dito",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8E2020),
        )
    }
}
