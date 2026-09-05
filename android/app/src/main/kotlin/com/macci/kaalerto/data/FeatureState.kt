package com.macci.kaalerto.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Materialized reducer output for one feature (road segment, area) — fully rebuildable
 * from [Event] at any time, so this table is a cache of the fold, not a source of truth.
 *
 * The reducer that actually populates this (Rules A-D, confidence, SX conflict
 * detection) is BUILD_TASKS.md day 4. This schema exists now so day 4 has a landing
 * spot; nothing writes to it yet.
 */
@Entity(tableName = "feature_state")
data class FeatureState(
    @PrimaryKey val featureRef: String,
    val severity: String?,
    val confidence: Double,
    val bucket: String,
    val isConflicted: Boolean,
    val lastReportMs: Long,
)
