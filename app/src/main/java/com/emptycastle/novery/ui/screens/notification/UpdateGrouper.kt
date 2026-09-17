package com.emptycastle.novery.ui.screens.notification

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Slice-05.1: groups update items by recency of their notification
 * timestamp. Pure logic (injectable clock/zone) — unit-tested.
 */
enum class UpdateGroup(val title: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    EARLIER("Earlier")
}

object UpdateGrouper {

    fun groupKey(
        timestampMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): UpdateGroup {
        val date = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        // Clock-skew safety: anything from "the future" reads as today.
        if (!date.isBefore(today)) return UpdateGroup.TODAY
        return when (date) {
            today.minusDays(1) -> UpdateGroup.YESTERDAY
            else -> UpdateGroup.EARLIER
        }
    }

    /**
     * Groups items (already sorted) preserving order within each bucket.
     * Buckets with no items are omitted.
     */
    fun <T> group(
        items: List<T>,
        timestampOf: (T) -> Long,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Pair<UpdateGroup, List<T>>> {
        if (items.isEmpty()) return emptyList()
        val buckets = linkedMapOf<UpdateGroup, MutableList<T>>()
        for (item in items) {
            buckets.getOrPut(groupKey(timestampOf(item), nowMs, zone)) { mutableListOf() }
                .add(item)
        }
        return UpdateGroup.entries.mapNotNull { group ->
            buckets[group]?.takeIf { it.isNotEmpty() }?.let { group to it.toList() }
        }
    }

    /** Start-of-day millis, exposed for tests. */
    fun startOfDayMs(timestampMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val date: LocalDate = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
