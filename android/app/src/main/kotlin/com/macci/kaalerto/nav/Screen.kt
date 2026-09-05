package com.macci.kaalerto.nav

/**
 * The whole app is 2-3 screens right now, so this is a plain sealed switch rather than
 * a navigation graph — revisit once there are enough screens (detail sheet, SOS, family
 * check-in, ...) that a real back stack earns its keep.
 */
sealed interface Screen {
    data object Map : Screen
    data object PickLocation : Screen
    data class Report(val lat: Double, val lon: Double, val accuracyMeters: Float?) : Screen
}
