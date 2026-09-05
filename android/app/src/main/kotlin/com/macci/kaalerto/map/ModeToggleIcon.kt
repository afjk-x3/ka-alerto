package com.macci.kaalerto.map

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Matches Map-Normal.dc.html's top-right mode icon exactly (an 8-ray sun glyph, drawn
 * rather than pulled from the icon library since this project deliberately ships only
 * material-icons-core, not -extended, and a sun isn't in that curated set). Storm mode
 * swaps it for a crescent moon so the icon itself communicates which way the tap goes.
 */
@Composable
fun ModeToggleIcon(stormMode: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.09f
        val stroke = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val center = Offset(size.width / 2f, size.height / 2f)

        if (!stormMode) {
            val coreRadius = size.minDimension * 0.17f
            val rayInner = size.minDimension * 0.30f
            val rayOuter = size.minDimension * 0.42f
            drawCircle(color = tint, radius = coreRadius, center = center, style = stroke)
            repeat(8) { i ->
                val angle = Math.toRadians((i * 45).toDouble())
                val from = Offset(
                    center.x + (rayInner * cos(angle)).toFloat(),
                    center.y + (rayInner * sin(angle)).toFloat(),
                )
                val to = Offset(
                    center.x + (rayOuter * cos(angle)).toFloat(),
                    center.y + (rayOuter * sin(angle)).toFloat(),
                )
                drawLine(color = tint, start = from, end = to, strokeWidth = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        } else {
            val radius = size.minDimension * 0.32f
            val path = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(center = center, radius = radius))
            }
            val cutout = androidx.compose.ui.graphics.Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        center = Offset(center.x + radius * 0.55f, center.y - radius * 0.35f),
                        radius = radius * 0.85f,
                    ),
                )
            }
            val crescent = androidx.compose.ui.graphics.Path().apply {
                op(path, cutout, androidx.compose.ui.graphics.PathOperation.Difference)
            }
            drawPath(crescent, color = tint)
        }
    }
}
