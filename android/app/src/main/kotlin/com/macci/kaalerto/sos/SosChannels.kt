package com.macci.kaalerto.sos

import com.macci.kaalerto.mesh.MeshStatus

/**
 * BUILD_TASKS.md day 8's "honest per-channel status". The artboard renders this as
 * `Rescue centre: Sinusubukan · SMS: naipadala 4 min ago · Mga kalapit na phone: 3`.
 *
 * Two of those three channels do not exist yet — SMS is day 12, server sync is day 13 —
 * and this build says so rather than showing a plausible "Sinusubukan…" next to a
 * transport that nothing is even attempting. That is the whole point of the word
 * "honest" in the task: a spinner over a channel with no code behind it is precisely
 * the fake-success this project refuses (docs/03-architecture.md §6.4.4, "Never a
 * spinner, never a fake success"). The rows still appear, so the screen shows the real
 * shape of the escalation and what is missing from it.
 */
enum class SosChannel(val fil: String, val en: String) {
    SERVER("Rescue centre", "Rescue centre"),
    SMS("SMS", "SMS"),
    MESH("Mga kalapit na phone", "Nearby phones"),
}

sealed interface ChannelStatus {
    /** No code attempts this channel yet. [buildDay] is when it is scheduled. */
    data class NotBuilt(val buildDay: String) : ChannelStatus

    /** The radio is up and this request is in what peers reconcile against. */
    data class Broadcasting(val peerCount: Int) : ChannelStatus

    /** The transport itself is unavailable — radios off, no permission. */
    data class Unavailable(val reason: String) : ChannelStatus
}

data class SosChannelRow(val channel: SosChannel, val status: ChannelStatus)

/**
 * The channel rows as they honestly stand right now.
 *
 * The mesh row is real: it reads the live [MeshStatus] the day 6-7 service publishes.
 * Note what it does *not* claim — "3 phone ang may dala ng SOS mo" (3 phones are
 * carrying your SOS) is the artboard's copy for [SosState.RELAYED], and needs a peer to
 * acknowledge storage, which is day 9. Until then this says only that peers are
 * connected and the request is being offered to them.
 */
fun sosChannelRows(mesh: MeshStatus): List<SosChannelRow> = listOf(
    SosChannelRow(SosChannel.SERVER, ChannelStatus.NotBuilt("build day 13")),
    SosChannelRow(SosChannel.SMS, ChannelStatus.NotBuilt("build day 12")),
    SosChannelRow(
        SosChannel.MESH,
        when {
            mesh.error != null -> ChannelStatus.Unavailable(mesh.error)
            mesh.running -> ChannelStatus.Broadcasting(mesh.peerCount)
            else -> ChannelStatus.Unavailable("Hindi tumatakbo ang mesh")
        },
    ),
)

/** The one-line Filipino status shown on the right of each row. */
fun ChannelStatus.shortLabel(): String = when (this) {
    is ChannelStatus.NotBuilt -> "Hindi pa gawa"
    is ChannelStatus.Broadcasting -> if (peerCount > 0) "$peerCount konektado" else "Nagba-broadcast"
    is ChannelStatus.Unavailable -> "Hindi magamit"
}

/** The explanatory second line. Says what is true, including when what is true is "nothing yet". */
fun ChannelStatus.detail(): String = when (this) {
    is ChannelStatus.NotBuilt -> "Wala pang code sa build na ito — $buildDay"
    is ChannelStatus.Broadcasting ->
        if (peerCount > 0) {
            "Inaalok ang SOS sa $peerCount kalapit na phone"
        } else {
            "Naghahanap ng kalapit na phone"
        }
    is ChannelStatus.Unavailable -> reason
}
