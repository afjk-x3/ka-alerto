package com.macci.kaalerto.identity

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val ROSTER_ASSET = "barangay_roster.json"
private val rosterJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * One seat on the sangguniang barangay, as shipped in `assets/barangay_roster.json`.
 *
 * **Seats, not people.** The composition is real (RA 7160 §387 fixes it at one Punong
 * Barangay, seven Kagawad and one SK Chairperson); who holds each seat in San Juan
 * Bautista has never been collected, and putting invented names against real elected
 * offices would be fabricating a public record. So the fixture carries the offices and
 * a device supplies the person — its own display name, at the moment it claims.
 *
 * This roster is the *only* source of the official role. An official cannot grant
 * official to anyone: authority comes from holding barangay office, so it enters the
 * system exactly once, at a seat claim, and never spreads sideways.
 */
@Serializable
data class BarangaySeat(
    val id: String,
    val title: String,
    val kind: String,
    /** False would be a seat that exists but may not rule on flood severity. */
    val canPostOfficialStatus: Boolean = true,
)

@Serializable
private data class RosterFile(val seats: List<BarangaySeat> = emptyList())

fun loadBarangayRoster(context: Context): List<BarangaySeat> = runCatching {
    val raw = context.assets.open(ROSTER_ASSET).bufferedReader().use { it.readText() }
    rosterJson.decodeFromString(RosterFile.serializer(), raw).seats
}.getOrElse { emptyList() }
