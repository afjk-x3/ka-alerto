package com.macci.kaalerto.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Normal-mode [androidx.compose.material3.ColorScheme], built from
 * design/artboards/Palettes.dc.html's "Normal" column rather than a generated
 * Material baseline — this app has its own fixed palette per docs/02-prd.md §5.6, not
 * a dynamic/tonal one.
 *
 * The `surfaceContainer*`/`surfaceBright`/`surfaceDim` family is set explicitly too —
 * these are what `DropdownMenu`, `AlertDialog` and `ModalBottomSheet` actually pull
 * their background from, not plain `surface`. Leaving them at [lightColorScheme]'s
 * generated baseline is harmless here (the light defaults are close to white anyway),
 * but Storm below shows why it can't be skipped. `surfaceTint` is forced transparent so
 * Material's elevation-tint overlay (blended from `primary`) never nudges a surface off
 * the exact hex this project specifies.
 */
private val NormalColorScheme = lightColorScheme(
    primary = NormalPrimaryAction,
    onPrimary = NormalOnPrimaryAction,
    background = NormalBackground,
    onBackground = NormalPrimaryText,
    surface = NormalBackground,
    onSurface = NormalPrimaryText,
    surfaceVariant = NormalRecessedSurface,
    onSurfaceVariant = NormalSecondaryText,
    outline = NormalBorder,
    outlineVariant = NormalBorderEmphasis,
    error = NormalCriticalFg,
    errorContainer = NormalCriticalBg,
    onError = Color.White,
    onErrorContainer = NormalCriticalFg,
    inverseSurface = NormalPrimaryText,
    inverseOnSurface = NormalBackground,
    surfaceBright = NormalBackground,
    surfaceDim = NormalRecessedSurface,
    surfaceContainerLowest = NormalBackground,
    surfaceContainerLow = NormalBackground,
    surfaceContainer = NormalBackground,
    surfaceContainerHigh = NormalRecessedSurface,
    surfaceContainerHighest = NormalRecessedSurface,
    surfaceTint = Color.Transparent,
    scrim = Color.Black,
)

/**
 * Storm mode's [androidx.compose.material3.ColorScheme] — built from
 * [darkColorScheme] rather than [lightColorScheme] this time. It was built on
 * `lightColorScheme` originally on the theory that overriding every slot this project
 * actually uses would make the base irrelevant; that missed the `surfaceContainer*`
 * family, which several stock Material components (`DropdownMenu` among them) read
 * their background from — the result was a light-lavender popup on a dark screen.
 * Starting from `darkColorScheme` means anything still missed here defaults to
 * something dark, not light.
 */
private val StormColorScheme = darkColorScheme(
    primary = StormPrimaryText,
    onPrimary = StormBackground,
    background = StormBackground,
    onBackground = StormPrimaryText,
    surface = StormBackground,
    onSurface = StormPrimaryText,
    surfaceVariant = StormElevatedSurface,
    onSurfaceVariant = StormSecondaryText,
    outline = StormBorder,
    outlineVariant = StormBorder,
    error = StormCriticalAccent,
    errorContainer = StormCriticalBg,
    onError = Color.White,
    onErrorContainer = StormCriticalAccent,
    inverseSurface = StormPrimaryText,
    inverseOnSurface = StormBackground,
    surfaceBright = StormElevatedSurface,
    surfaceDim = StormBackground,
    surfaceContainerLowest = StormBackground,
    surfaceContainerLow = StormBackground,
    surfaceContainer = StormElevatedSurface,
    surfaceContainerHigh = StormElevatedSurface,
    surfaceContainerHighest = StormElevatedSurface,
    surfaceTint = Color.Transparent,
    scrim = Color.Black,
)

/**
 * @param stormMode A resident- or barangay-declared condition (docs/02-prd.md §6), not
 *   the phone's system dark-mode setting — deliberately not `isSystemInDarkTheme()`.
 */
@Composable
fun KaAlertoTheme(
    stormMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (stormMode) StormColorScheme else NormalColorScheme
    val extraColors = if (stormMode) StormKaAlertoColors else NormalKaAlertoColors

    CompositionLocalProvider(LocalKaAlertoColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
