package com.macci.kaalerto.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Severity palette.
//
// These do NOT change with Normal / Storm / Survival mode. A colour meaning
// "impassable" must not mean something else in another theme (design/README.md).
// Never move these into the mode-dependent schemes below.
// ---------------------------------------------------------------------------
val SeverityS0Cleared = Color(0xFF2F7FBF)     // cleared
val SeverityS1Caution = Color(0xFFF2A93B)     // passable with caution
val SeverityS2NoCars = Color(0xFFE4682B)      // impassable for cars
val SeverityS3Impassable = Color(0xFFC42B2B)  // impassable for all
// SX (conflicting) is rendered as a hatch pattern, not a flat colour.

// ---------------------------------------------------------------------------
// Normal mode — design/artboards/Palettes.dc.html, "Normal" column (12 tokens).
// ---------------------------------------------------------------------------
val NormalBackground = Color(0xFFFFFFFF)
val NormalCanvas = Color(0xFFF7F5F2)
val NormalRecessedSurface = Color(0xFFF2F4F6)
val NormalPrimaryText = Color(0xFF14171A)
val NormalSecondaryText = Color(0xFF5C666F)
val NormalDisabledText = Color(0xFF8A939B)
val NormalBorder = Color(0xFFE3E7EB)
val NormalBorderEmphasis = Color(0xFFD8DEE3)
val NormalPrimaryAction = Color(0xFF14171A)
val NormalOnPrimaryAction = Color(0xFFFFFFFF)
val NormalSafeBg = Color(0xFFE4F1E9)
val NormalSafeFg = Color(0xFF2E7D4F)
val NormalWarningBg = Color(0xFFFFF8E8)
val NormalWarningFg = Color(0xFFA3791A)
val NormalCriticalBg = Color(0xFFFBEBE9)
val NormalCriticalFg = Color(0xFFA32020)

// ---------------------------------------------------------------------------
// Storm mode — design/artboards/Palettes.dc.html, "Storm" column (6 tokens).
// "Dark is not a preference here. It saves OLED power during an outage and
// stays readable at minimum brightness."
// ---------------------------------------------------------------------------
val StormBackground = Color(0xFF0D0F12)
val StormElevatedSurface = Color(0xFF1A1D21)
val StormPrimaryText = Color(0xFFF2F4F7)
val StormSecondaryText = Color(0xFFA8B0BA)
val StormCriticalAccent = Color(0xFFFF5A4E)
val StormSafeAccent = Color(0xFF4FA3E3)
val StormBorder = Color(0xFF3A4149)
val StormDivider = Color(0xFF23272C)
/**
 * Not in Palettes.dc.html's 6-token Storm list — needed because Material3's
 * `contentColorFor()` matches a `Surface`'s color against the scheme's container
 * roles by value. Reusing [StormElevatedSurface] for both `surfaceVariant` and
 * `errorContainer` made them ambiguous, so a plain neutral card (background =
 * surfaceVariant) started rendering its default text in `onErrorContainer`'s red
 * instead of `onSurfaceVariant`'s grey. This just needs to be a distinct value.
 */
val StormCriticalBg = Color(0xFF2A1414)
