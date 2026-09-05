package com.macci.kaalerto.sos

import com.macci.kaalerto.data.Event

/** What a redacted SOS shows instead of the requester's name. */
const val REDACTED_AUTHOR = "Hindi ipinapakita"

/**
 * What an SOS is allowed to carry when it leaves this device.
 *
 * `docs/03-architecture.md` §6.5 says mesh-relaying peers store an **encrypted** payload
 * they cannot read — only a coarse routing header in the clear — so that "neighbours
 * relay a rescue request without learning that the family down the street needs
 * dialysis". Ground rule 4 says no crypto in this build. Those two cannot both hold, so
 * the sensitive fields are **removed** rather than encrypted: what is not sent cannot be
 * read off a relaying phone.
 *
 * Two fields go:
 *
 *  - **Medical needs.** Sensitive personal information under RA 10173, and the design
 *    already restricts it: QueueVolunteer.dc.html's own footer says a registered
 *    volunteer gets "lokasyon at bilang ng tao", while "ang detalyeng medikal ay hawak
 *    ng barangay official". With no keys to hold it *for* the official, it stays on the
 *    requester's device. Medical-to-officials is designed-not-built.
 *  - **The requester's display name.** SOSNearby.dc.html is explicit that a resident is
 *    not shown "kung sino sila". The name-is-public decision in CLAUDE.md was about
 *    attribution on a *flood report* someone chose to file — it does not extend to
 *    broadcasting who is trapped in which house to every phone in Bluetooth range.
 *
 * What deliberately still travels: coordinates, timestamp, hop data, and the people
 * count. A responder who cannot see how many people to plan for is not much use, and
 * the people count is exactly what the volunteer tier is entitled to.
 *
 * **The residual, stated plainly:** the *coordinates* still travel in the clear, because
 * a rescue needs them and there is no key to hold them under. A non-responder's screen
 * coarsens them to an approximate circle (see [com.macci.kaalerto.sos.SosNearbyScreen]),
 * but that is a display choice, not a guarantee — anyone dumping a relaying device would
 * find the exact point. Only real §6.5 encryption fixes that, and it is out of scope
 * here.
 *
 * Redaction happens on the way **out**, so it is irreversible at the first hop: a peer
 * that relays onward is passing on what it received, which never contained the detail.
 */
fun redactForMesh(event: Event): Event = when (event.type) {
    TYPE_SOS, TYPE_SOS_AMEND -> event.copy(
        authorName = REDACTED_AUTHOR,
        payload = redactPayload(event.payload),
    )
    // `sos_state` carries the *responder's* name, not the requester's, and that is the
    // point — the artboard's own copy is "Papunta na si Boy". Someone volunteering to
    // walk into floodwater is entitled to be identified for it.
    else -> event
}

private fun redactPayload(raw: String?): String? {
    val payload = decodeSosPayload(raw) ?: return raw
    val context = payload.context ?: return raw
    return payload.copy(context = context.copy(medical = emptyList())).encode()
}

/** True if this event would lose something by being relayed — used by tests and logging. */
fun carriesSensitiveDetail(event: Event): Boolean {
    if (event.type != TYPE_SOS && event.type != TYPE_SOS_AMEND) return false
    val medical = decodeSosPayload(event.payload)?.context?.medical.orEmpty()
    return medical.any { it != SosContext.MEDICAL_NONE } || event.authorName != REDACTED_AUTHOR
}
