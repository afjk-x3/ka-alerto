package com.macci.kaalerto.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.macci.kaalerto.data.FeatureSummary
import com.macci.kaalerto.ui.theme.SeverityColors

/**
 * One marker "kind" per Map-Normal.dc.html's icon language — colour, ring style and a
 * distinct glyph together, not colour alone (Foundations.dc.html: "never colour alone").
 * A stale feature is rendered by its own neutral kind regardless of severity: the
 * artboard treats "needs checking" as the primary signal once a report has decayed, not
 * a footnote on the old colour.
 */
enum class MarkerKind(val key: String) {
    S0("S0"), S1("S1"), S2("S2"), S3("S3"), CONFLICT("SX"), STALE("STALE");

    companion object {
        fun forSummary(summary: FeatureSummary): MarkerKind = when {
            summary.isStale -> STALE
            summary.isConflicted -> CONFLICT
            else -> entries.firstOrNull { it.key == summary.severity } ?: S0
        }
    }
}

private const val ICON_DP = 32
private const val ICON_DENSITY = 3f // renders at a fixed density; MapLibre symbols aren't dp-scaled like Compose
private const val ICON_PX = (ICON_DP * ICON_DENSITY).toInt()

fun markerIconName(kind: MarkerKind): String = "marker-${kind.key}"

/** Registers one bitmap per [MarkerKind] on the style — call once per style load. */
fun registerMarkerIcons(style: org.maplibre.android.maps.Style) {
    MarkerKind.entries.forEach { kind ->
        style.addImage(markerIconName(kind), renderMarkerIcon(kind))
    }
}

private fun renderMarkerIcon(kind: MarkerKind): Bitmap {
    val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = ICON_PX / 2f
    val radius = ICON_PX * 0.36f

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ICON_PX * 0.07f
    }
    val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ICON_PX * 0.06f
        strokeCap = Paint.Cap.ROUND
    }

    when (kind) {
        MarkerKind.S1, MarkerKind.S2, MarkerKind.S3 -> {
            fill.color = Color.parseColor(SeverityColors.forSeverity(kind.key))
            canvas.drawCircle(center, center, radius, fill)
            ring.color = Color.WHITE
            canvas.drawCircle(center, center, radius, ring)
            glyph.color = if (kind == MarkerKind.S1) Color.parseColor("#14171A") else Color.WHITE
            drawGlyph(canvas, kind, center, radius, glyph)
        }
        MarkerKind.S0 -> {
            fill.color = Color.WHITE
            canvas.drawCircle(center, center, radius, fill)
            ring.color = Color.parseColor(SeverityColors.S0)
            canvas.drawCircle(center, center, radius, ring)
            glyph.color = Color.parseColor(SeverityColors.S0)
            drawGlyph(canvas, kind, center, radius, glyph)
        }
        MarkerKind.CONFLICT -> {
            fill.color = Color.WHITE
            canvas.drawCircle(center, center, radius, fill)
            // Ring colour matches Map-Normal.dc.html's own conflict marker (#E4682B, S2's
            // orange) — the artboard deliberately doesn't use a third "conflict colour".
            ring.color = Color.parseColor(SeverityColors.S2)
            canvas.drawCircle(center, center, radius, ring)
            glyph.color = Color.parseColor("#14171A")
            glyph.style = Paint.Style.FILL
            glyph.textSize = radius * 1.2f
            glyph.textAlign = Paint.Align.CENTER
            glyph.isFakeBoldText = true
            canvas.drawText("?", center, center + radius * 0.42f, glyph)
        }
        MarkerKind.STALE -> {
            fill.color = Color.WHITE
            canvas.drawCircle(center, center, radius, fill)
            ring.color = Color.parseColor("#B9A98F")
            ring.pathEffect = DashPathEffect(floatArrayOf(ICON_PX * 0.06f, ICON_PX * 0.05f), 0f)
            canvas.drawCircle(center, center, radius, ring)
            glyph.color = Color.parseColor("#8A939B")
            drawGlyph(canvas, kind, center, radius, glyph)
        }
    }
    return bitmap
}

/** Distinct per-kind glyph, per Foundations.dc.html: "each carries a distinct icon". */
private fun drawGlyph(canvas: Canvas, kind: MarkerKind, cx: Float, r: Float, paint: Paint) {
    val s = r * 0.55f
    when (kind) {
        MarkerKind.S1 -> {
            // wave — matches the amber marker's "≈" in Map-Normal.dc.html
            val path = Path()
            path.moveTo(cx - s, cx - s * 0.15f)
            path.cubicTo(cx - s * 0.5f, cx - s * 0.75f, cx, cx + s * 0.45f, cx + s * 0.5f, cx - s * 0.15f)
            path.moveTo(cx - s, cx + s * 0.5f)
            path.cubicTo(cx - s * 0.5f, cx - s * 0.1f, cx, cx + s * 1.1f, cx + s * 0.5f, cx + s * 0.5f)
            canvas.drawPath(path, paint)
        }
        MarkerKind.S2 -> {
            // barrier bars — impassable for cars
            canvas.drawLine(cx - s, cx - s * 0.3f, cx + s, cx - s * 0.3f, paint)
            canvas.drawLine(cx - s, cx + s * 0.3f, cx + s, cx + s * 0.3f, paint)
        }
        MarkerKind.S3 -> {
            // exclamation — impassable for all
            canvas.drawLine(cx, cx - s, cx, cx + s * 0.3f, paint)
            canvas.drawCircle(cx, cx + s * 0.85f, r * 0.06f, paint.apply { style = Paint.Style.FILL })
        }
        MarkerKind.S0 -> {
            // checkmark — cleared
            val path = Path()
            path.moveTo(cx - s * 0.75f, cx)
            path.lineTo(cx - s * 0.1f, cx + s * 0.6f)
            path.lineTo(cx + s * 0.8f, cx - s * 0.6f)
            canvas.drawPath(path, paint)
        }
        MarkerKind.STALE -> {
            // clock — needs checking
            canvas.drawCircle(cx, cx, s * 0.7f, paint)
            canvas.drawLine(cx, cx, cx, cx - s * 0.4f, paint)
            canvas.drawLine(cx, cx, cx + s * 0.3f, cx + s * 0.1f, paint)
        }
        MarkerKind.CONFLICT -> Unit
    }
}
