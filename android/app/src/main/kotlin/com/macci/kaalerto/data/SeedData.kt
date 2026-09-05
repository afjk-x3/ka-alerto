package com.macci.kaalerto.data

import kotlinx.serialization.Serializable

/** Mirrors `assets/seed_data.json`. The `_meta` key is present in the file but unused here. */
@Serializable
data class SeedFile(val reports: List<SeedReport>)

/**
 * `timestampMinutesAgo` and `ttlMinutes` are offsets, not absolute times — see the
 * fixture's own `_meta.schemaNote`. [SeedLoader] resolves them against wall clock at
 * load time; nothing here persists the raw offsets.
 */
@Serializable
data class SeedReport(
    val id: String,
    val type: String,
    val lat: Double,
    val lon: Double,
    val featureRef: String,
    val severity: String? = null,
    val waterLevel: String? = null,
    val authorId: String,
    val authorName: String,
    val authorRole: String,
    val timestampMinutesAgo: Long,
    val ttlMinutes: Long,
    val origin: String,
    val hopCount: Int,
    val note: String? = null,
)
