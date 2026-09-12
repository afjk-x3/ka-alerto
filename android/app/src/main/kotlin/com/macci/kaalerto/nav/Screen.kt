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

    /**
     * PRD §9's registration. [resume] is where to go once it is done — null on a first
     * run (the map), or the report that sent someone here, so registering never drops
     * what they were doing.
     */
    data class Onboarding(val resume: Screen?) : Screen
}
