package com.macci.kaalerto.nav

/**
 * The whole app is a handful of screens, so this is a plain sealed switch rather than a
 * navigation graph — revisit once there are enough of them (family check-in, the
 * responder queues, ...) that a real back stack earns its keep.
 */
sealed interface Screen {
    data object Map : Screen
    data object PickLocation : Screen
    data class Report(val lat: Double, val lon: Double, val accuracyMeters: Float?) : Screen

    /**
     * The SOS path — SOSHold → SOSContext → SOSStatus → RescueCard (design/README.md).
     * The last of those is reached automatically as well as by tap: the rescue card is
     * a *state*, not a destination, and appears when no channel has produced anything.
     */
    data class SosHold(val lat: Double, val lon: Double, val accuracyMeters: Float?) : Screen
    data class SosAddContext(val sosId: String) : Screen
    data class SosStatus(val sosId: String) : Screen
    data class SosRescueCard(val sosId: String) : Screen
}
