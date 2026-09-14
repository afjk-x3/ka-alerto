package com.macci.kaalerto.map

import com.macci.kaalerto.data.FeatureSummary
import com.macci.kaalerto.detail.PHILIPPINE_TIME
import java.time.Instant
import java.time.ZoneId

/**
 * Count of distinct flood_report events (not confirms/disputes) filed today, for [MapHeader].
 * "Today" is the Philippine calendar day, the same one the detail sheet's "Ngayong araw"
 * uses — on a phone left on another zone the two used to disagree about the same report.
 */
fun reportsToday(summaries: List<FeatureSummary>, nowMs: Long, zone: ZoneId = PHILIPPINE_TIME): Int {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return summaries.asSequence()
        .flatMap { it.events.asSequence() }
        .filter { it.type == "flood_report" }
        .distinctBy { it.id }
        .count { event ->
            Instant.ofEpochMilli(event.timestampMs).atZone(zone).toLocalDate() == today
        }
}
