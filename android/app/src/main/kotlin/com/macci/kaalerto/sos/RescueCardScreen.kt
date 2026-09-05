package com.macci.kaalerto.sos

import android.app.Activity
import android.view.WindowManager
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.demo.DemoArea
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * design/artboards/RescueCard.dc.html — Lighthouse Mode's last resort
 * (`docs/03-architecture.md` §6.4.3): "designed to be readable through a window, from a
 * boat, or photographed by someone else's phone".
 *
 * Three deliberate departures from every other screen in the app, all from §6.4 and
 * design/README.md:
 *
 *  - **White at full brightness, in every mode**, including Storm and Survival. A card a
 *    stranger has to read in the dark is worth the power. The screen states that cost in
 *    its own black strip rather than quietly draining the battery.
 *  - **No app chrome.** Coordinates in 34sp monospace are the largest text in the
 *    product, because they are the one thing that has to survive being read at distance.
 *  - **The QR carries the request itself**, not a link to it — see [SosCard]. Whoever
 *    scans it has no network either.
 */
@Composable
fun RescueCardScreen(
    snapshot: SosSnapshot,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alarm = remember { SosAlarm() }
    var sounding by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // §6.4.3's "readable through a window" is a brightness claim as much as a contrast
    // one, so the card actually takes the screen to full brightness — and gives it back
    // on the way out, since nothing else in the app has any business running there.
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val previous = window?.attributes?.screenBrightness
        window?.attributes = window?.attributes?.apply { screenBrightness = 1f }
        onDispose {
            if (window != null && previous != null) {
                window.attributes = window.attributes.apply { screenBrightness = previous }
            }
            alarm.stop()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SosColors.CardBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SosColors.Critical)
                .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 14.dp),
        ) {
            Text("KAILANGAN NG SAGIP", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SosColors.CardBackground)
            Text("RESCUE NEEDED · show this screen", fontSize = 15.sp, color = SosColors.CriticalText)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SosColors.CardInk)
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BatteryGlyph(SosColors.CardBackground, Modifier.size(17.dp))
            Spacer(Modifier.size(9.dp))
            Text(
                "Mabilis kumonsumo ng baterya ang screen na ito. Puti at pinakamaliwanag para makita ka.",
                fontSize = 12.sp,
                color = SosColors.CardBackground,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        ) {
            CardLabel("LOKASYON")
            Text(
                "%.4f".format(snapshot.lat),
                fontFamily = FontFamily.Monospace,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = SosColors.CardInk,
            )
            Text(
                "%.4f".format(snapshot.lon),
                fontFamily = FontFamily.Monospace,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = SosColors.CardInk,
            )
            Text(
                buildString {
                    snapshot.accuracyMeters?.let { append("±${it.toInt()} m · ") }
                    append(DemoArea.BARANGAY_NAME)
                },
                fontSize = 15.sp,
                color = SosColors.CardMuted,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        Divider()

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                CardLabel("TAO")
                Text(
                    snapshot.context.people ?: "?",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = SosColors.CardInk,
                )
                Text(
                    snapshot.context.companions.joinToString(" · ").ifEmpty { "Hindi sinabi" },
                    fontSize = 14.sp,
                    color = SosColors.CardInk,
                )
            }
            Box(Modifier.size(width = 2.dp, height = 96.dp).background(SosColors.CardInk))
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                CardLabel("TUBIG")
                Text(
                    snapshot.context.water ?: "Hindi sinabi",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = SosColors.CardInk,
                )
                snapshot.context.trend?.let {
                    Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SosColors.Critical)
                }
            }
        }
        Divider()

        if (snapshot.context.hasMedicalNeed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SosColors.CardMedicalBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MedicalCrossGlyph(SosColors.Critical, Modifier.size(22.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    snapshot.context.medical.filterNot { it == SosContext.MEDICAL_NONE }.joinToString(" · "),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = SosColors.CardInk,
                )
            }
            Divider()
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QrCode(
                content = snapshot.toCard().encode(),
                modifier = Modifier.size(150.dp),
            )
            Spacer(Modifier.size(16.dp))
            Column {
                Text("I-scan ito kung may app ka", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SosColors.CardInk)
                Text(
                    "Scanning this passes the rescue request to your phone, even with no network.",
                    fontSize = 14.sp,
                    color = SosColors.CardMuted,
                )
            }
        }

        Text(
            "Ipinadala ${timeFormat.format(Date(snapshot.startedAtMs))} · patuloy pa rin ang pag-broadcast",
            fontSize = 15.sp,
            color = SosColors.CardMuted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )

        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .background(if (sounding) SosColors.Critical else SosColors.CardInk)
                    .clickable {
                        if (sounding) {
                            alarm.stop()
                            sounding = false
                        } else {
                            sounding = true
                            alarm.start(scope) { sounding = false }
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeakerGlyph(SosColors.CardBackground, Modifier.size(22.dp))
                Spacer(Modifier.size(9.dp))
                Text(
                    if (sounding) "Itigil" else "Patunugin",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SosColors.CardBackground,
                )
            }
            // The artboard's second control here is "Kumurap" (strobe). It is not built:
            // BUILD_TASKS.md day 8's cut list is "cut strobe and context screen; keep
            // the QR card", and a button that flashes nothing is worse than no button.
            // "Bumalik" takes its place so the card is escapable without the system back
            // gesture, which is not obvious on a screen with no chrome.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .border(2.dp, SosColors.CardInk)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("Bumalik", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SosColors.CardInk)
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(3.dp).background(SosColors.CardInk))
}

@Composable
private fun CardLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = SosColors.CardMuted,
    )
}

/**
 * Draws the module grid straight onto a Canvas.
 *
 * Module size is floored to a whole pixel and the grid is centred in whatever is left
 * over, so every module is the same size and lands on an exact pixel boundary. A QR
 * scaled by a fractional factor gets soft module edges, and soft edges are what makes a
 * code that decodes perfectly in a unit test fail against a real camera.
 *
 * The 4-module quiet zone is part of the spec, not padding — many decoders will not
 * even attempt a code without it.
 */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) { encodeQr(content) }
    Canvas(modifier = modifier.background(SosColors.CardBackground)) {
        val quietZone = 4
        val total = matrix.size + quietZone * 2
        val module = kotlin.math.floor(minOf(size.width, size.height) / total)
        if (module < 1f) return@Canvas
        val drawn = module * total
        val originX = (size.width - drawn) / 2f + module * quietZone
        val originY = (size.height - drawn) / 2f + module * quietZone

        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix[x, y]) continue
                drawRect(
                    color = SosColors.CardInk,
                    topLeft = Offset(originX + x * module, originY + y * module),
                    size = Size(module, module),
                )
            }
        }
    }
}

@Composable
private fun BatteryGlyph(tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f)
        drawRect(tint, topLeft = Offset(w * 0.1f, h * 0.29f), size = Size(w * 0.63f, h * 0.42f), style = stroke)
        drawRect(tint, topLeft = Offset(w * 0.82f, h * 0.44f), size = Size(w * 0.08f, h * 0.13f))
        drawRect(tint, topLeft = Offset(w * 0.2f, h * 0.38f), size = Size(w * 0.1f, h * 0.24f))
    }
}

@Composable
private fun MedicalCrossGlyph(tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val thickness = w * 0.22f
        drawRect(tint, topLeft = Offset(w * 0.5f - thickness / 2, h * 0.12f), size = Size(thickness, h * 0.76f))
        drawRect(tint, topLeft = Offset(w * 0.12f, h * 0.5f - thickness / 2), size = Size(w * 0.76f, thickness))
    }
}
