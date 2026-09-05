package com.macci.kaalerto.sos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * The red live-SOS banner. Identical on the context and status screens and never
 * mode-dependent, per SOSContext.dc.html's own comment on it — while a request is
 * live, every screen has to say so in the same place, in the same colour.
 */
@Composable
fun SosLiveBanner(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SosColors.Critical)
            .padding(start = 16.dp, end = 16.dp, top = 44.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(11.dp).background(SosColors.CardBackground, CircleShape))
        Spacer(Modifier.size(11.dp))
        Column {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SosColors.CardBackground)
            Text(
                subtitle,
                fontSize = 13.sp,
                color = SosColors.CriticalText,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** "mm:ss" for the first hour, then "N min". What the banner counts up. */
fun elapsedLabel(startedAtMs: Long, nowMs: Long): String {
    val seconds = ((nowMs - startedAtMs).coerceAtLeast(0)) / 1000
    return if (seconds < 3_600) {
        "%d:%02d".format(seconds / 60, seconds % 60)
    } else {
        "${seconds / 60} min"
    }
}

/** The three expanding arcs over a dot — the artboards' "broadcasting" glyph. */
@Composable
fun BroadcastGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.09f
        drawCircle(tint, radius = h * 0.11f, center = Offset(w * 0.5f, h * 0.73f))
        listOf(0.42f to 0.75f, 0.72f to 0.4f).forEach { (radiusFraction, alpha) ->
            val r = w * radiusFraction
            val path = Path().apply {
                moveTo(w * 0.5f - r, h * 0.73f - r * 0.62f)
                quadraticTo(w * 0.5f, h * 0.73f - r * 1.35f, w * 0.5f + r, h * 0.73f - r * 0.62f)
            }
            drawPath(path, color = tint.copy(alpha = alpha), style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
    }
}

/** Envelope — the SMS channel row. */
@Composable
fun EnvelopeGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.085f)
        drawRect(tint, topLeft = Offset(w * 0.12f, h * 0.22f), size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.56f), style = stroke)
        val flap = Path().apply {
            moveTo(w * 0.12f, h * 0.28f)
            lineTo(w * 0.5f, h * 0.53f)
            lineTo(w * 0.88f, h * 0.28f)
        }
        drawPath(flap, color = tint, style = stroke)
    }
}

/** Two stacked racks — the server / rescue-centre channel row. */
@Composable
fun ServerGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.08f)
        val size2 = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.29f)
        drawRect(tint, topLeft = Offset(w * 0.12f, h * 0.16f), size = size2, style = stroke)
        drawRect(tint, topLeft = Offset(w * 0.12f, h * 0.56f), size = size2, style = stroke)
        drawCircle(tint, radius = w * 0.035f, center = Offset(w * 0.26f, h * 0.305f))
        drawCircle(tint, radius = w * 0.035f, center = Offset(w * 0.26f, h * 0.705f))
    }
}

/** Shield with a check — "Ligtas na ako". */
@Composable
fun SafeShieldGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.09f, cap = StrokeCap.Round)
        val shield = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.85f, h * 0.25f)
            lineTo(w * 0.85f, h * 0.52f)
            cubicTo(w * 0.85f, h * 0.75f, w * 0.7f, h * 0.86f, w * 0.5f, h * 0.92f)
            cubicTo(w * 0.3f, h * 0.86f, w * 0.15f, h * 0.75f, w * 0.15f, h * 0.52f)
            lineTo(w * 0.15f, h * 0.25f)
            close()
        }
        drawPath(shield, color = tint, style = stroke)
        val check = Path().apply {
            moveTo(w * 0.34f, h * 0.5f)
            lineTo(w * 0.46f, h * 0.62f)
            lineTo(w * 0.68f, h * 0.38f)
        }
        drawPath(check, color = tint, style = stroke)
    }
}

/** Speaker with sound waves — the rescue card's "Patunugin". */
@Composable
fun SpeakerGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.085f, cap = StrokeCap.Round)
        val cone = Path().apply {
            moveTo(w * 0.17f, h * 0.375f)
            lineTo(w * 0.17f, h * 0.625f)
            lineTo(w * 0.34f, h * 0.625f)
            lineTo(w * 0.55f, h * 0.79f)
            lineTo(w * 0.55f, h * 0.21f)
            lineTo(w * 0.34f, h * 0.375f)
            close()
        }
        drawPath(cone, color = tint, style = stroke)
        listOf(0.16f to 0.72f, 0.28f to 0.86f).forEach { (dx, endY) ->
            val path = Path().apply {
                moveTo(w * (0.68f + dx - 0.16f), h * (0.5f - (endY - 0.5f)))
                quadraticTo(w * (0.86f + dx - 0.16f), h * 0.5f, w * (0.68f + dx - 0.16f), h * endY)
            }
            drawPath(path, color = tint, style = stroke)
        }
    }
}
