package com.macci.kaalerto.sos

import androidx.compose.ui.graphics.Color

/**
 * SOS chrome is always urgent-styled, whatever Normal/Storm mode the phone is in — one of
 * the design system's two deliberate exceptions to mode theming (design/README.md), the
 * other being the rescue card's white surface. Fixed values from RescueCard.dc.html and
 * Map-Normal.dc.html rather than theme tokens, so a theme change can never make this
 * screen look calm by accident. A subset of feat/event-sourced-roles' `SosColors`.
 */
object SosColors {
    val Critical = Color(0xFFC42B2B)
    val CriticalText = Color(0xFFFFD9D6)

    /** Rescue card — a white surface at full brightness, whatever the mode. */
    val CardBackground = Color(0xFFFFFFFF)
    val CardInk = Color(0xFF000000)
    val CardMuted = Color(0xFF5C666F)
}
