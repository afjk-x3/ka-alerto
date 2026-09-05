package com.macci.kaalerto.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Roles from design/artboards/Palettes.dc.html that don't map onto a stock Material3
 * [androidx.compose.material3.ColorScheme] slot — canvas vs. background, and the
 * safe/warning/critical status pairs (each an explicit bg+fg pair, not a tint of
 * `error`). [androidx.compose.material3.MaterialTheme.colorScheme] still covers the
 * roles that do map (surface, onSurface, outline, primary, errorContainer, ...).
 */
data class KaAlertoColors(
    val canvas: Color,
    val recessedSurface: Color,
    val border: Color,
    val borderEmphasis: Color,
    val safeBg: Color,
    val safeFg: Color,
    val warningBg: Color,
    val warningFg: Color,
    val criticalBg: Color,
    val criticalFg: Color,
)

val NormalKaAlertoColors = KaAlertoColors(
    canvas = NormalCanvas,
    recessedSurface = NormalRecessedSurface,
    border = NormalBorder,
    borderEmphasis = NormalBorderEmphasis,
    safeBg = NormalSafeBg,
    safeFg = NormalSafeFg,
    warningBg = NormalWarningBg,
    warningFg = NormalWarningFg,
    criticalBg = NormalCriticalBg,
    criticalFg = NormalCriticalFg,
)

/**
 * Storm's palette is deliberately 6 tokens, not 12 (Palettes.dc.html: "the palette
 * gets smaller as the mode degrades — that is the design, not an omission"). Roles
 * Storm doesn't name explicitly (recessed surface, safe/warning bg) fall back to the
 * elevated surface / background rather than inventing colours the design doesn't have.
 */
val StormKaAlertoColors = KaAlertoColors(
    canvas = StormBackground,
    recessedSurface = StormElevatedSurface,
    border = StormBorder,
    borderEmphasis = StormBorder,
    safeBg = StormElevatedSurface,
    safeFg = StormSafeAccent,
    warningBg = StormElevatedSurface,
    warningFg = SeverityS1Caution,
    criticalBg = StormCriticalBg,
    criticalFg = StormCriticalAccent,
)

val LocalKaAlertoColors = staticCompositionLocalOf { NormalKaAlertoColors }
