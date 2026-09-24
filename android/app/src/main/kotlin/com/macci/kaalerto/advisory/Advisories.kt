package com.macci.kaalerto.advisory

import com.macci.kaalerto.data.Event
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import java.time.OffsetDateTime
import javax.xml.parsers.DocumentBuilderFactory

/**
 * PRD FR-3.3: PAGASA advisories relayed verbatim, beside but never blended with community
 * reports. PAGASA publishes them as CAP (Common Alerting Protocol) messages in a public Atom
 * feed, CC BY 4.0 ([PAGASA_FEED]). A phone with internet stores each one that covers its
 * home province as one [TYPE_ADVISORY] event, and the event then travels like any other —
 * Bluetooth relay to phones with no signal, Supabase to the rest.
 *
 * The event id is PAGASA's own CAP identifier and the author is always "PAGASA", so every
 * phone that fetches the same alert writes the byte-identical event and the copies collapse.
 * Nothing here is derived, scored or translated: the text shown is the text PAGASA sent.
 */
const val TYPE_ADVISORY = "advisory"
const val PAGASA_FEED = "https://publicalert.pagasa.dost.gov.ph/feeds/"
const val PAGASA_AUTHOR = "PAGASA"

@Serializable
data class AdvisoryPayload(
    val capId: String,
    /** CAP msgType: Alert, Update or Cancel. An Update replaces, and a Cancel hides, the alerts it [references]. */
    val msgType: String = "Alert",
    val references: List<String> = emptyList(),
    /** "General Flood Advisory (Moderate)", "Tropical Cyclone Alert", … */
    val event: String = "",
    val headline: String = "",
    val description: String = "",
    val instruction: String = "",
    val areas: List<String> = emptyList(),
    val severity: String = "",
    val sent: String = "",
    val expires: String = "",
    val link: String = "",
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun AdvisoryPayload.encode(): String = json.encodeToString(AdvisoryPayload.serializer(), this)

fun decodeAdvisory(raw: String?): AdvisoryPayload? =
    raw?.let { runCatching { json.decodeFromString(AdvisoryPayload.serializer(), it) }.getOrNull() }

/** One Atom entry: PAGASA's id and where its CAP file is. */
data class FeedEntry(val capId: String, val title: String, val capUrl: String)

private fun parseXml(xml: String): Element? = runCatching {
    DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(xml.byteInputStream()).documentElement
}.getOrNull()

private fun Element.all(tag: String): List<Element> {
    val nodes = getElementsByTagName(tag)
    return (0 until nodes.length).map { nodes.item(it) as Element }
}

private fun Element.text(tag: String): String = all(tag).firstOrNull()?.textContent?.trim().orEmpty()

fun parseFeed(xml: String): List<FeedEntry> = parseXml(xml)?.all("entry")?.mapNotNull { entry ->
    val id = entry.text("id").removePrefix("urn:uuid:")
    val cap = entry.all("link").firstOrNull { it.getAttribute("type").contains("cap") }?.getAttribute("href")
    if (id.isBlank() || cap.isNullOrBlank()) null else FeedEntry(id, entry.text("title"), cap)
}.orEmpty()

/** Null for anything that is not a CAP alert we can read. */
fun parseCap(xml: String, link: String = ""): AdvisoryPayload? {
    val alert = parseXml(xml) ?: return null
    val id = alert.text("identifier").ifBlank { return null }
    val info = alert.all("info").firstOrNull() ?: return null
    return AdvisoryPayload(
        capId = id,
        msgType = alert.text("msgType").ifBlank { "Alert" },
        // CAP references are "sender,identifier,sent" triples separated by spaces.
        references = alert.text("references").split(Regex("\\s+")).mapNotNull { it.split(",").getOrNull(1) },
        event = info.text("event"),
        headline = info.text("headline"),
        description = info.text("description"),
        instruction = info.text("instruction"),
        areas = info.all("areaDesc").map { it.textContent.trim() }.filter { it.isNotEmpty() },
        severity = info.text("severity"),
        sent = alert.text("sent"),
        expires = info.text("expires"),
        link = link,
    )
}

private fun epochMs(iso: String): Long? = runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()

/**
 * Whether the alert applies to [province]: PAGASA names it among the areas, or the alert is
 * nationwide (tropical cyclone alerts are issued for the "Philippine Area of Responsibility").
 */
fun AdvisoryPayload.covers(province: String): Boolean =
    areas.any { it.contains(province, ignoreCase = true) || it.contains("Philippine Area of Responsibility", ignoreCase = true) }

/** Null when the alert has no usable sent or expiry time. */
fun advisoryEvent(payload: AdvisoryPayload): Event? {
    val sent = epochMs(payload.sent) ?: return null
    // A Cancel carries no expiry of its own; it only has to outlive what it cancels.
    val expires = epochMs(payload.expires) ?: (sent + 24L * 60 * 60 * 1000)
    return Event(
        id = "pagasa-${payload.capId}",
        type = TYPE_ADVISORY,
        lat = 0.0,
        lon = 0.0,
        featureRef = null,
        severity = null,
        waterLevel = null,
        authorId = PAGASA_AUTHOR,
        authorName = PAGASA_AUTHOR,
        authorRole = "source",
        timestampMs = sent,
        expiresAt = expires,
        origin = "local",
        hopCount = 0,
        note = null,
        payload = payload.encode(),
    )
}

/**
 * Unexpired PAGASA alerts that cover [province], newest first. An alert that a later
 * Update or Cancel references is gone: PAGASA's update replaces it, a cancellation ends it.
 */
fun activeAdvisories(events: List<Event>, nowMs: Long, province: String): List<AdvisoryPayload> {
    val advisories = events
        .filter { it.type == TYPE_ADVISORY && it.authorId == PAGASA_AUTHOR && it.expiresAt > nowMs }
        .mapNotNull { e -> decodeAdvisory(e.payload)?.let { e to it } }
    // Stored events outlive their expiry by a day, so the chain is read from all of them,
    // not only the live ones: an expired Update still means its original was replaced.
    val superseded = events
        .filter { it.type == TYPE_ADVISORY && it.authorId == PAGASA_AUTHOR }
        .mapNotNull { decodeAdvisory(it.payload) }
        .filter { it.msgType.equals("Cancel", true) || it.msgType.equals("Update", true) }
        .flatMap { it.references }
        .toSet()
    return advisories
        .filter { (_, p) -> !p.msgType.equals("Cancel", true) && p.capId !in superseded && p.covers(province) }
        .sortedByDescending { it.first.timestampMs }
        .map { it.second }
}
