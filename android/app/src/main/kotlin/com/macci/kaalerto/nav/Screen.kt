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

    /** Day 9 — the receiving half. A resident's coarse view, and the responder queue. */
    data class SosNearby(val sosId: String) : Screen
    data object SosQueue : Screen

    /**
     * PRD §9's registration (7 Sep). [resume] is the screen the person was on their way
     * to when the gate stopped them — normally a [Report] with the GPS fix already
     * taken, so finishing the form lands them where they were going rather than back at
     * the map with the fetch to do again. Null when the screen was opened deliberately,
     * e.g. to correct a typo from the role screen.
     */
    data class Onboarding(val resume: Screen?) : Screen

    /**
     * The drawer's "Ang profile ko" (7 Sep) — editing an already-registered identity.
     * Separate from [Onboarding]: a first run is a required gate with no cancel, and an
     * edit is neither required nor gate-shaped. [resume] is never null here — a profile
     * edit always has an origin screen to return to, unlike a first-run gate, which may
     * be the very first screen the app ever shows.
     */
    data class Profile(val resume: Screen) : Screen

    /**
     * Pick-mode over the real map, for confirming the home pin during registration.
     * Separate from [PickLocation] only in where it returns to — the map itself, and
     * day 3's tap-to-pick, are the same. Works offline because it reads the same tile
     * pack everything else does; no reverse geocoding is involved, since a coordinate
     * is the thing being captured, not a place name.
     */
    data object PickHome : Screen

    /** Day 10 — the role switch, an official's ruling on one feature, and the centres. */
    data object Roles : Screen
    data class OfficialStatus(val featureRef: String) : Screen
    data object EvacCentres : Screen

    /** Build day 11a — a household circle joined by QR, plus the one-tap "Ligtas ako". */
    data object FamilyCircle : Screen

    /** QR scanner for family circle pairing. */
    data object QrScanner : Screen
}
