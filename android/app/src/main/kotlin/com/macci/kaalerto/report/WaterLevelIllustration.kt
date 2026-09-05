package com.macci.kaalerto.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.min

// Fraction of figure height, measured from the bottom, where the water line sits for
// each body depth. Deliberately not linear: the gap between waist and chest is small
// because both already read as S3 (BODY_LEVELS) — the picture shouldn't suggest a
// bigger jump than the severity does.
private val WATER_HEIGHT_FRACTION = mapOf(
    "ankle" to 0.10f,
    "knee" to 0.32f,
    "waist" to 0.50f,
    "chest" to 0.64f,
)

/**
 * A simple stick figure with a rising water line — the illustration on
 * design/artboards/Report-Normal.dc.html, redrawn natively since the artboard's own
 * illustration is hand-drawn SVG specific to the design-canvas host.
 */
@Composable
fun BodyIllustration(levelId: String?, waterColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val strokeColor = Color(0xFF14171A)
        val strokeWidth = min(w, h) * 0.03f

        // Head.
        val headRadius = h * 0.09f
        val headCenterY = h * 0.16f
        drawCircle(color = strokeColor, radius = headRadius, center = Offset(centerX, headCenterY), style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth))

        val neckY = headCenterY + headRadius
        val hipY = h * 0.56f
        val feetY = h * 0.95f

        // Torso.
        drawLine(strokeColor, Offset(centerX, neckY), Offset(centerX, hipY), strokeWidth, StrokeCap.Round)
        // Arms.
        val shoulderY = neckY + (hipY - neckY) * 0.15f
        drawLine(strokeColor, Offset(centerX, shoulderY), Offset(centerX - w * 0.22f, hipY * 0.9f), strokeWidth, StrokeCap.Round)
        drawLine(strokeColor, Offset(centerX, shoulderY), Offset(centerX + w * 0.22f, hipY * 0.9f), strokeWidth, StrokeCap.Round)
        // Legs.
        drawLine(strokeColor, Offset(centerX, hipY), Offset(centerX - w * 0.14f, feetY), strokeWidth, StrokeCap.Round)
        drawLine(strokeColor, Offset(centerX, hipY), Offset(centerX + w * 0.14f, feetY), strokeWidth, StrokeCap.Round)

        // Water: filled band from the derived height to the bottom of the canvas.
        val fraction = WATER_HEIGHT_FRACTION[levelId] ?: 0f
        val waterTopY = h * (1f - fraction)
        drawRect(
            color = waterColor.copy(alpha = 0.35f),
            topLeft = Offset(0f, waterTopY),
            size = androidx.compose.ui.geometry.Size(w, h - waterTopY),
        )
        drawLine(
            color = waterColor,
            start = Offset(0f, waterTopY),
            end = Offset(w, waterTopY),
            strokeWidth = strokeWidth * 0.8f,
        )
    }
}

/** Small distinguishing glyph per vehicle-scale option — see WaterLevel.kt for why there's no dynamic illustration for this mode. */
@Composable
fun VehicleGlyph(id: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = min(w, h) * 0.08f)
        when (id) {
            "car" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.08f, h * 0.35f),
                    size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.32f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
                    style = stroke,
                )
                drawCircle(tint, radius = h * 0.09f, center = Offset(w * 0.28f, h * 0.72f))
                drawCircle(tint, radius = h * 0.09f, center = Offset(w * 0.72f, h * 0.72f))
            }
            "truck" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.05f, h * 0.22f),
                    size = androidx.compose.ui.geometry.Size(w * 0.9f, h * 0.46f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
                    style = stroke,
                )
                drawCircle(tint, radius = h * 0.09f, center = Offset(w * 0.26f, h * 0.76f))
                drawCircle(tint, radius = h * 0.09f, center = Offset(w * 0.74f, h * 0.76f))
            }
            "motorcycle" -> {
                drawLine(tint, Offset(w * 0.22f, h * 0.55f), Offset(w * 0.78f, h * 0.55f), min(w, h) * 0.08f, StrokeCap.Round)
                drawCircle(tint, radius = h * 0.14f, center = Offset(w * 0.22f, h * 0.72f), style = stroke)
                drawCircle(tint, radius = h * 0.14f, center = Offset(w * 0.78f, h * 0.72f), style = stroke)
            }
            else -> {
                // "none" / nothing can pass — the universal no-entry glyph.
                drawCircle(tint, radius = w * 0.4f, center = Offset(w / 2f, h / 2f), style = stroke)
                val r = w * 0.4f
                val cx = w / 2f
                val cy = h / 2f
                val dx = r * 0.9f
                drawLine(tint, Offset(cx - dx, cy - dx), Offset(cx + dx, cy + dx), min(w, h) * 0.08f, StrokeCap.Round)
            }
        }
    }
}
