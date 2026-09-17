package com.emptycastle.novery.ui.screens.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Slice-05.1: update grouping. Pure JVM test with a fixed clock/zone.
 */
class UpdateGrouperTest {

    private val zone = ZoneId.of("UTC")
    private val day = 24L * 60 * 60 * 1000
    // 2026-09-17T12:00:00Z (a Thursday).
    private val now = 1789636800000L

    @Test
    fun sameDay_isToday() {
        assertEquals(
            UpdateGroup.TODAY,
            UpdateGrouper.groupKey(now - 60_000L, now, zone)
        )
        assertEquals(
            UpdateGroup.TODAY,
            UpdateGrouper.groupKey(UpdateGrouper.startOfDayMs(now, zone), now, zone)
        )
    }

    @Test
    fun yesterdayWindow_isYesterday() {
        val startOfToday = UpdateGrouper.startOfDayMs(now, zone)
        assertEquals(
            UpdateGroup.YESTERDAY,
            UpdateGrouper.groupKey(startOfToday - 1_000L, now, zone)
        )
        assertEquals(
            UpdateGroup.YESTERDAY,
            UpdateGrouper.groupKey(startOfToday - day + 1_000L, now, zone)
        )
    }

    @Test
    fun older_isEarlier() {
        assertEquals(
            UpdateGroup.EARLIER,
            UpdateGrouper.groupKey(now - 2 * day, now, zone)
        )
        assertEquals(
            UpdateGroup.EARLIER,
            UpdateGrouper.groupKey(now - 365 * day, now, zone)
        )
    }

    @Test
    fun future_timestamps_groupAsToday() {
        // Clock skew safety: the future belongs to no past bucket.
        assertEquals(
            UpdateGroup.TODAY,
            UpdateGrouper.groupKey(now + day, now, zone)
        )
    }

    @Test
    fun group_preservesOrderAndOmitsEmptyBuckets() {
        val items = listOf(
            "t1" to now - 1_000L,
            "t2" to now - 2_000L,
            "y1" to (UpdateGrouper.startOfDayMs(now, zone) - 1_000L),
            "e1" to (now - 5 * day)
        )
        val grouped = UpdateGrouper.group(items, { it.second }, now, zone)
        assertEquals(3, grouped.size)
        assertEquals(UpdateGroup.TODAY, grouped[0].first)
        assertEquals(listOf("t1", "t2"), grouped[0].second.map { it.first })
        assertEquals(UpdateGroup.YESTERDAY, grouped[1].first)
        assertEquals(UpdateGroup.EARLIER, grouped[2].first)
    }

    @Test
    fun group_empty_returnsEmpty() {
        assertTrue(UpdateGrouper.group(emptyList<Pair<String, Long>>(), { it.second }, now, zone).isEmpty())
    }
}
