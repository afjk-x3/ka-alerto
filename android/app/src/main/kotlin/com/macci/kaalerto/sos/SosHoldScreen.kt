package com.macci.kaalerto.sos

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** BUILD_TASKS.md day 8: "press-and-hold with 1.5s haptic countdown ring". */
private const val HOLD_DURATION_MS = 1_500L
private const val HOLD_TICK_MS = 16L

/**
 * design/artboards/SOSHold.dc.html. The long press is not friction for its own sake —
 * the artboard says why in its own copy: "Kailangan ng mahabang pindot para hindi ito
 * maaksidenteng ma-send sa bulsa mo." A pocket-dialled rescue costs a dispatch.
 *
 * Two things are shown before the hold completes, both from the artboard's "Ipapadala
 * agad" panel: the coordinates that will go out and the time. Nothing is hidden behind
 * the press, so nobody discovers what they sent afterwards.
 */
@Composable
fun SosHoldScreen(
    lat: Double,
    lon: Double,
    accuracyMeters: Float?,
    onHoldComplete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = remember { SosHaptics(context) }
    var progress by remember { mutableFloatStateOf(0f) }
    var holding by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }

    // The countdown runs here rather than inside the gesture handler so that releasing
    // early cancels it by flipping `holding`, and so the haptic ticks stay on the same
    // clock as the ring rather than drifting against it.
    LaunchedEffect(holding) {
        if (!holding) {
            progress = 0f
            return@LaunchedEffect
        }
        var elapsed = 0L
        var ticksFired = 0
        while (isActive && elapsed < HOLD_DURATION_MS) {
            delay(HOLD_TICK_MS)
            elapsed += HOLD_TICK_MS
            progress = (elapsed.toFloat() / HOLD_DURATION_MS).coerceIn(0f, 1f)
            // Three ticks across the hold, accelerating in perceived urgency because
            // they are evenly spaced against a ring that is visibly closing.
            val expectedTicks = (progress * 3).toInt()
            if (expectedTicks > ticksFired) {
                ticksFired = expectedTicks
                haptics.tick()
            }
        }
        if (isActive) {
            completed = true
            haptics.confirm()
            onHoldComplete()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SosColors.HoldBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            "Humingi ng tulong",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = SosColors.PrimaryText,
        )
        Text("Request rescue", fontSize = 16.sp, color = SosColors.HoldSecondaryText)

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            HoldRing(
                progress = progress,
                holding = holding,
                modifier = Modifier
                    .size(248.dp)
                    .pointerInput(completed) {
                        if (completed) return@pointerInput
                        detectTapGestures(
                            onPress = {
                                holding = true
                                // Returns when the finger lifts or the gesture is
                                // cancelled; either way the hold is over.
                                tryAwaitRelease()
                                holding = false
                            },
                        )
                    },
            )
        }

        Text(
            if (holding) "Huwag bitawan…" else "Pindutin at hawakan",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = SosColors.PrimaryText,
        )
        Text(
            "Kailangan ng mahabang pindot para hindi ito\nmaaksidenteng ma-send sa bulsa mo.",
            fontSize = 14.sp,
            color = SosColors.HoldSecondaryText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )

        OutgoingPanel(
            lat = lat,
            lon = lon,
            accuracyMeters = accuracyMeters,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp),
        )

        Box(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 26.dp)
                .fillMaxWidth()
                .height(56.dp)
                .border(1.5.dp, SosColors.HoldBorder)
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Text("Kanselahin", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SosColors.HoldSecondaryText)
        }
    }
}

/**
 * The artboard's 248 dp ring: a dark track, an arc that closes clockwise from 12
 * o'clock as the hold progresses, and the red SOS disc inside it.
 */
@Composable
private fun HoldRing(progress: Float, holding: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2f + 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = SosColors.CriticalTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (progress > 0f) {
                drawArc(
                    color = SosColors.CriticalOnDark,
                    // -90 so it starts at the top, like the artboard's rotate(-90deg).
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(30.dp)
                .fillMaxSize()
                .background(SosColors.Critical, CircleShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "SOS",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = SosColors.CardBackground,
            )
            Text(if (holding) "Hawakan" else "Pindutin", fontSize = 17.sp, color = SosColors.CriticalText)
            Text("keep holding", fontSize = 13.sp, color = SosColors.CriticalSoft)
        }
    }
}

/** SOSHold.dc.html's "Ipapadala agad" panel — what leaves the phone the instant the hold lands. */
@Composable
private fun OutgoingPanel(lat: Double, lon: Double, accuracyMeters: Float?, modifier: Modifier = Modifier) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SosColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "IPAPADALA AGAD",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = SosColors.MutedText,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            PinGlyph(SosColors.Mesh, Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Text(
                "%.4f, %.4f".format(lat, lon),
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                color = SosColors.PrimaryText,
            )
            if (accuracyMeters != null) {
                Spacer(Modifier.size(8.dp))
                Text("±${accuracyMeters.toInt()} m", fontSize = 13.sp, color = SosColors.MutedText)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClockGlyph(SosColors.SecondaryText, Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Text("${timeFormat.format(Date())} · ngayon", fontSize = 15.sp, color = SosColors.PrimaryText)
        }
        Text(
            "Madadagdagan mo ng detalye pagkatapos — hindi hinihintay ng pagpapadala.",
            fontSize = 13.sp,
            color = SosColors.SecondaryText,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Vibration rather than Compose's `LocalHapticFeedback`, which offers only two
 * semantic constants (`LongPress`, `TextHandleMove`) — enough to mark an event, not to
 * pace a countdown someone is meant to feel closing.
 */
private class SosHaptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun tick() = vibrate(18, VibrationEffect.DEFAULT_AMPLITUDE)

    /** Longer and at full amplitude: §6.1's t+0.0 "haptic confirmation" that it is out. */
    fun confirm() = vibrate(140, 255)

    private fun vibrate(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude)) }
    }
}

@Composable
private fun PinGlyph(tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.94f)
            cubicTo(w * 0.5f, h * 0.94f, w * 0.85f, h * 0.55f, w * 0.85f, h * 0.38f)
            cubicTo(w * 0.85f, h * 0.15f, w * 0.68f, h * 0.06f, w * 0.5f, h * 0.06f)
            cubicTo(w * 0.32f, h * 0.06f, w * 0.15f, h * 0.15f, w * 0.15f, h * 0.38f)
            cubicTo(w * 0.15f, h * 0.55f, w * 0.5f, h * 0.94f, w * 0.5f, h * 0.94f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = w * 0.11f))
        drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.38f), style = Stroke(width = w * 0.11f))
    }
}

@Composable
private fun ClockGlyph(tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        drawCircle(tint, radius = w * 0.4f, center = Offset(w / 2f, h / 2f), style = stroke)
        drawLine(tint, Offset(w * 0.5f, h * 0.28f), Offset(w * 0.5f, h * 0.52f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.52f), Offset(w * 0.69f, h * 0.64f), stroke.width, StrokeCap.Round)
    }
}
