package com.macci.kaalerto.sos

import androidx.compose.ui.graphics.Color

/**
 * SOS chrome is **always** urgent-styled regardless of Normal/Storm/Survival mode —
 * one of only two deliberate exceptions to mode theming in the design system
 * (design/README.md), the other being the rescue card's white surface. So these are
 * fixed values from the artboards rather than tokens read off `MaterialTheme`: the
 * whole point is that this screen looks the same whatever mode the phone is in, and
 * routing it through the theme would make that an accident waiting to be broken.
 *
 * Values are taken from SOSHold / SOSContext / SOSStatus / RescueCard .dc.html.
 */
object SosColors {
    /** SOSHold's warm near-black. Distinct from Storm's neutral #0D0F12 on purpose. */
    val HoldBackground = Color(0xFF1A0E0D)
    val Background = Color(0xFF0D0F12)
    val Surface = Color(0xFF1A1D21)
    val Divider = Color(0xFF23272C)

    val PrimaryText = Color(0xFFF2F4F7)
    val SecondaryText = Color(0xFFA8B0BA)
    val MutedText = Color(0xFF7C858E)
    /** The warm muted grey used only on SOSHold, against its warm ground. */
    val HoldSecondaryText = Color(0xFFC9A9A5)
    val HoldBorder = Color(0xFF4A2A26)
    val Border = Color(0xFF4A525A)

    /** The live-SOS banner and the hold button's core. */
    val Critical = Color(0xFFC42B2B)
    val CriticalOnDark = Color(0xFFFF5A4E)
    val CriticalText = Color(0xFFFFD9D6)
    val CriticalSoft = Color(0xFFFF9A92)
    val CriticalTrack = Color(0xFF3A1D1B)
    val CriticalSurface = Color(0xFF4A1512)

    val Mesh = Color(0xFF4FA3E3)
    val Warning = Color(0xFFE0A93B)

    /** Rescue card — a white surface at full brightness, whatever the battery state. */
    val CardBackground = Color(0xFFFFFFFF)
    val CardInk = Color(0xFF000000)
    val CardMuted = Color(0xFF5C666F)
    val CardMedicalBg = Color(0xFFFFE9E7)
}
