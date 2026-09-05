package com.macci.kaalerto.detail

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

/** Warning triangle + exclamation, for the conflict "ruling" card — DetailConflict-*.dc.html. */
@Composable
fun WarningTriangleIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.09f, cap = StrokeCap.Round)
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.94f, h * 0.86f)
            lineTo(w * 0.06f, h * 0.86f)
            close()
        }
        drawPath(path, color = tint, style = stroke)
        drawLine(tint, Offset(w * 0.5f, h * 0.4f), Offset(w * 0.5f, h * 0.62f), stroke.width, StrokeCap.Round)
        drawCircle(tint, radius = h * 0.045f, center = Offset(w * 0.5f, h * 0.73f))
    }
}

/** "Tama" confirm button icon. */
@Composable
fun CheckIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.16f, h * 0.5f)
            lineTo(w * 0.42f, h * 0.74f)
            lineTo(w * 0.86f, h * 0.24f)
        }
        drawPath(path, color = tint, style = Stroke(width = min(w, h) * 0.11f, cap = StrokeCap.Round))
    }
}

/** "Iba na" dispute button icon. */
@Composable
fun XIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.11f, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.78f, h * 0.78f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.22f, h * 0.78f), stroke.width, StrokeCap.Round)
    }
}

/** "I-check ko ngayon" button icon. */
@Composable
fun MagnifierIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.1f, cap = StrokeCap.Round)
        val r = min(w, h) * 0.32f
        val center = Offset(w * 0.44f, h * 0.44f)
        drawCircle(tint, radius = r, center = center, style = stroke)
        val handleStart = Offset(center.x + r * 0.75f, center.y + r * 0.75f)
        drawLine(tint, handleStart, Offset(w * 0.92f, h * 0.92f), stroke.width, StrokeCap.Round)
    }
}

/** Mesh-relay origin icon (three nodes, two edges) — matches the artboard's "Mesh" value glyph. */
@Composable
fun MeshIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = min(w, h) * 0.14f
        val left = Offset(w * 0.2f, h * 0.5f)
        val topRight = Offset(w * 0.8f, h * 0.22f)
        val bottomRight = Offset(w * 0.8f, h * 0.78f)
        val stroke = Stroke(width = min(w, h) * 0.09f)
        drawLine(tint, left, topRight, stroke.width, StrokeCap.Round)
        drawLine(tint, left, bottomRight, stroke.width, StrokeCap.Round)
        drawCircle(tint, radius = r, center = left, style = stroke)
        drawCircle(tint, radius = r, center = topRight, style = stroke)
        drawCircle(tint, radius = r, center = bottomRight, style = stroke)
    }
}

/**
 * Per-bucket confidence glyph — design/artboards/Foundations.dc.html's "Confidence"
 * panel: unverified (circle + exclamation), likely (circle + short tick), confirmed
 * (circle + check), official (shield + check). The dashed-vs-solid ring the artboard
 * uses for unverified is drawn on the *card border* at the call site, not the icon.
 */
@Composable
fun ConfidenceIcon(bucket: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.11f, cap = StrokeCap.Round)
        when (bucket) {
            "official" -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.08f)
                    lineTo(w * 0.86f, h * 0.24f)
                    lineTo(w * 0.86f, h * 0.5f)
                    cubicTo(w * 0.86f, h * 0.78f, w * 0.7f, h * 0.9f, w * 0.5f, h * 0.96f)
                    cubicTo(w * 0.3f, h * 0.9f, w * 0.14f, h * 0.78f, w * 0.14f, h * 0.5f)
                    lineTo(w * 0.14f, h * 0.24f)
                    close()
                }
                drawPath(path, color = tint, style = stroke)
                val check = Path().apply {
                    moveTo(w * 0.32f, h * 0.5f)
                    lineTo(w * 0.46f, h * 0.64f)
                    lineTo(w * 0.7f, h * 0.36f)
                }
                drawPath(check, color = tint, style = Stroke(width = stroke.width * 0.85f, cap = StrokeCap.Round))
            }
            "confirmed" -> {
                drawCircle(tint, radius = min(w, h) * 0.4f, center = Offset(w / 2f, h / 2f), style = stroke)
                val check = Path().apply {
                    moveTo(w * 0.3f, h * 0.52f)
                    lineTo(w * 0.44f, h * 0.66f)
                    lineTo(w * 0.72f, h * 0.36f)
                }
                drawPath(check, color = tint, style = Stroke(width = stroke.width * 0.9f, cap = StrokeCap.Round))
            }
            "likely" -> {
                drawCircle(tint, radius = min(w, h) * 0.4f, center = Offset(w / 2f, h / 2f), style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.24f), Offset(w * 0.5f, h * 0.56f), stroke.width, StrokeCap.Round)
            }
            else -> {
                drawCircle(tint, radius = min(w, h) * 0.4f, center = Offset(w / 2f, h / 2f), style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.3f), Offset(w * 0.5f, h * 0.56f), stroke.width, StrokeCap.Round)
                drawCircle(tint, radius = min(w, h) * 0.045f, center = Offset(w * 0.5f, h * 0.7f))
            }
        }
    }
}

/**
 * The map marker / severity-badge glyph, matched per severity — a person icon with a
 * diagonal "impassable" slash for S2/S3, a wave for S1, a plain checkmark for S0. Same
 * icon language as the map markers (map/MarkerIcons.kt), redrawn here for Compose UI
 * since that file targets MapLibre's bitmap-based SymbolLayer, not a @Composable.
 */
@Composable
fun SeverityBadgeIcon(severity: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.13f, cap = StrokeCap.Round)
        when (severity) {
            "S0" -> {
                val check = Path().apply {
                    moveTo(w * 0.18f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.74f)
                    lineTo(w * 0.86f, h * 0.28f)
                }
                drawPath(check, color = tint, style = stroke)
            }
            "S1" -> {
                val path = Path().apply {
                    moveTo(w * 0.1f, h * 0.42f)
                    quadraticTo(w * 0.3f, h * 0.22f, w * 0.5f, h * 0.42f)
                    quadraticTo(w * 0.7f, h * 0.62f, w * 0.9f, h * 0.42f)
                }
                drawPath(path, color = tint, style = Stroke(width = stroke.width * 0.8f, cap = StrokeCap.Round))
                val path2 = Path().apply {
                    moveTo(w * 0.1f, h * 0.66f)
                    quadraticTo(w * 0.3f, h * 0.46f, w * 0.5f, h * 0.66f)
                    quadraticTo(w * 0.7f, h * 0.86f, w * 0.9f, h * 0.66f)
                }
                drawPath(path2, color = tint, style = Stroke(width = stroke.width * 0.8f, cap = StrokeCap.Round))
            }
            else -> {
                // S2/S3 — person icon plus a corner-to-corner impassable slash.
                drawCircle(tint, radius = h * 0.15f, center = Offset(w * 0.5f, h * 0.22f), style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.4f), Offset(w * 0.5f, h * 0.66f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.66f), Offset(w * 0.32f, h * 0.88f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.66f), Offset(w * 0.68f, h * 0.88f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.12f, h * 0.92f), Offset(w * 0.88f, h * 0.08f), stroke.width * 0.85f, StrokeCap.Round)
            }
        }
    }
}

/** SX's canonical rendering everywhere in this design system: a hatch, not a flat colour. */
fun conflictHatchBrush(): Brush = Brush.linearGradient(
    colorStops = arrayOf(
        0.0f to Color(0xFFC42B2B),
        0.5f to Color(0xFFC42B2B),
        0.5f to Color(0xFFF2A93B),
        1.0f to Color(0xFFF2A93B),
    ),
    start = Offset(0f, 0f),
    end = Offset(28f, 28f),
    tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
)
