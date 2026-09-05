package com.macci.kaalerto.report

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
import kotlin.math.min
import kotlin.math.sin

/**
 * Fraction of figure height, measured from the bottom, where the water line sits for
 * each body depth. Deliberately not linear: the gap between waist and chest is small
 * because both already read as S3 (BODY_LEVELS) — the picture shouldn't suggest a
 * bigger jump than the severity does. Matches design/artboards/Report-Normal.dc.html's
 * own `waterY` values (168/138/108/78 out of a 212-tall figure), converted to a
 * bottom-relative fraction.
 */
private val WATER_HEIGHT_FRACTION = mapOf(
    "ankle" to (212f - 168f) / 212f,
    "knee" to (212f - 138f) / 212f,
    "waist" to (212f - 108f) / 212f,
    "chest" to (212f - 78f) / 212f,
)

/**
 * The stick-figure-in-rising-water illustration from Report-Normal.dc.html /
 * Report-Storm.dc.html, redrawn natively (the artboard's own illustration is SVG
 * specific to the design-canvas host). Matched to the artboard's exact anatomy — a
 * horizontal T-pose, not a downward V — plus two improvements the static artboard can't
 * show: the water line animates to its new depth instead of snapping, and a slow,
 * continuous ripple keeps the surface from reading as a flat, dead rectangle.
 */
@Composable
fun BodyIllustration(
    levelId: String?,
    waterColor: Color,
    modifier: Modifier = Modifier,
    // Off for small inline uses (e.g. detail/DetailSheet.kt's reading card, showing a
    // past, settled reading) — the ripple is meant to draw the eye on the report form,
    // not compete for attention next to text describing something already over.
    animate: Boolean = true,
) {
    val colors = LocalKaAlertoColors.current
    val strokeColor = MaterialTheme.colorScheme.onBackground
    val backgroundColor = colors.recessedSurface

    val targetFraction = WATER_HEIGHT_FRACTION[levelId] ?: 0f
    val waterFraction by if (animate) {
        animateFloatAsState(targetValue = targetFraction, label = "waterLevel")
    } else {
        androidx.compose.runtime.remember(targetFraction) { androidx.compose.runtime.mutableFloatStateOf(targetFraction) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waterRipple")
    val wavePhase by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
            label = "wavePhase",
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val strokeWidth = min(w, h) * 0.028f

        drawRoundRect(color = backgroundColor, size = size, cornerRadius = CornerRadius(w * 0.04f))

        // Figure — a horizontal T-pose (head, spine, arms, legs), matching the
        // artboard's proportions exactly rather than the more casual downward-V arms a
        // from-scratch stick figure would default to.
        run {
            val headRadius = h * 0.066f
            val headCenterY = h * 0.16f
            val neckY = headCenterY + headRadius
            val hipY = h * 0.52f
            val feetY = h * 0.955f
            val armY = neckY + (hipY - neckY) * 0.18f
            val armSpan = w * 0.17f

            drawCircle(color = backgroundColor, radius = headRadius, center = Offset(centerX, headCenterY))
            drawCircle(color = strokeColor, radius = headRadius, center = Offset(centerX, headCenterY), style = Stroke(width = strokeWidth))
            drawLine(strokeColor, Offset(centerX, neckY), Offset(centerX, hipY), strokeWidth, StrokeCap.Round)
            drawLine(strokeColor, Offset(centerX - armSpan, armY), Offset(centerX + armSpan, armY), strokeWidth, StrokeCap.Round)
            drawLine(strokeColor, Offset(centerX, hipY), Offset(centerX - w * 0.107f, feetY), strokeWidth, StrokeCap.Round)
            drawLine(strokeColor, Offset(centerX, hipY), Offset(centerX + w * 0.107f, feetY), strokeWidth, StrokeCap.Round)

            // Ground.
            drawLine(
                color = colors.border,
                start = Offset(w * 0.04f, h * 0.925f),
                end = Offset(w * 0.96f, h * 0.925f),
                strokeWidth = strokeWidth * 0.7f,
            )

            // Water — drawn last so it occludes the ground/legs it has risen past,
            // exactly like the artboard's own layer order.
            val waterTopY = h * (1f - waterFraction)
            val waveAmplitude = h * 0.018f
            val waveLength = w * 0.32f

            val fillPath = Path().apply {
                moveTo(0f, h)
                lineTo(0f, waterTopY)
                var x = 0f
                while (x <= w) {
                    val y = waterTopY + waveAmplitude * sin((x / waveLength) * 2 * Math.PI.toFloat() + wavePhase)
                    lineTo(x, y)
                    x += 4f
                }
                lineTo(w, waterTopY)
                lineTo(w, h)
                close()
            }
            drawPath(fillPath, color = waterColor.copy(alpha = 0.32f))

            val crestPath = Path().apply {
                var x = 0f
                var started = false
                while (x <= w) {
                    val y = waterTopY + waveAmplitude * sin((x / waveLength) * 2 * Math.PI.toFloat() + wavePhase)
                    if (!started) {
                        moveTo(x, y)
                        started = true
                    } else {
                        lineTo(x, y)
                    }
                    x += 4f
                }
            }
            drawPath(crestPath, color = waterColor, style = Stroke(width = strokeWidth * 0.75f, cap = StrokeCap.Round))
        }
    }
}

/** Small distinguishing glyph per vehicle-scale option — see WaterLevel.kt for why there's no dynamic illustration for this mode. */
@Composable
fun VehicleGlyph(id: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.08f)
        when (id) {
            "car" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.08f, h * 0.35f),
                    size = Size(w * 0.84f, h * 0.32f),
                    cornerRadius = CornerRadius(w * 0.08f),
                    style = stroke,
                )
                drawCircle(tint, radius = h * 0.09f, center = Offset(w * 0.28f, h * 0.72f))
                drawCircle(tint, radius = h * 0.09f, center = Offset(w * 0.72f, h * 0.72f))
            }
            "truck" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.05f, h * 0.22f),
                    size = Size(w * 0.9f, h * 0.46f),
                    cornerRadius = CornerRadius(w * 0.05f),
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

/** Local-save reassurance icon for the submit footer — not in this project's curated material-icons-core subset, so drawn like the other glyphs on this screen. */
@Composable
fun ShieldGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.03f)
            lineTo(w * 0.92f, h * 0.2f)
            lineTo(w * 0.92f, h * 0.5f)
            cubicTo(w * 0.92f, h * 0.78f, w * 0.74f, h * 0.94f, w * 0.5f, h * 1.0f)
            cubicTo(w * 0.26f, h * 0.94f, w * 0.08f, h * 0.78f, w * 0.08f, h * 0.5f)
            lineTo(w * 0.08f, h * 0.2f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = min(w, h) * 0.09f, cap = StrokeCap.Round))
    }
}

/** Small walking-person glyph for the "Katawan" scale toggle, matching Report-Normal.dc.html's tab icons. */
@Composable
fun PersonGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.11f, cap = StrokeCap.Round)
        drawCircle(tint, radius = h * 0.13f, center = Offset(w * 0.5f, h * 0.18f), style = stroke)
        drawLine(tint, Offset(w * 0.5f, h * 0.32f), Offset(w * 0.5f, h * 0.62f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.62f), Offset(w * 0.32f, h * 0.9f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.62f), Offset(w * 0.68f, h * 0.9f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.28f, h * 0.44f), Offset(w * 0.72f, h * 0.44f), stroke.width, StrokeCap.Round)
    }
}
