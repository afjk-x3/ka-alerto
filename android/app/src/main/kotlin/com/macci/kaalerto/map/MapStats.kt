package com.macci.kaalerto.map

import com.macci.kaalerto.data.FeatureSummary
import java.time.Instant
import java.time.ZoneId

/** Count of distinct flood_report events (not confirms/disputes) filed today, for [MapHeader]. */
fun reportsToday(summaries: List<FeatureSummary>, nowMs: Long): Int {
    val today = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return summaries.asSequence()
        .flatMap { it.events.asSequence() }
        .filter { it.type == "flood_report" }
        .distinctBy { it.id }
        .count { event ->
            Instant.ofEpochMilli(event.timestampMs).atZone(ZoneId.systemDefault()).toLocalDate() == today
        }
}
