package com.emptycastle.novery.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-05.3: error prune + relative labels. Pure JVM test.
 */
class UpdateErrorStoreTest {

    private val now = 1_700_000_000_000L
    private fun err(url: String, ts: Long) = UpdateError(
        novelUrl = url,
        novelName = "N",
        providerName = "P",
        message = "boom",
        timestamp = ts
    )

    @Test
    fun prune_dropsOldAndCapsCount() {
        val fresh = (0 until 25).map { i -> err("u$i", now - i * 1_000L) }
        val pruned = UpdateErrorStore.prune(fresh, now)
        assertEquals(UpdateErrorStore.MAX_ENTRIES, pruned.size)
        // Newest first.
        assertTrue(pruned.zipWithNext { a, b -> a.timestamp >= b.timestamp }.all { it })
    }

    @Test
    fun prune_dropsOlderThanAWeek() {
        val old = err("old", now - 8L * 24 * 60 * 60 * 1000)
        val fresh = err("new", now - 1_000L)
        val pruned = UpdateErrorStore.prune(listOf(old, fresh), now)
        assertEquals(listOf("new"), pruned.map { it.novelUrl })
    }

    @Test
    fun timeAgo_labels() {
        assertEquals("just now", UpdateErrorStore.timeAgo(now, now))
        assertEquals("just now", UpdateErrorStore.timeAgo(now + 60_000L, now))
        assertEquals("5m ago", UpdateErrorStore.timeAgo(now - 5L * 60_000, now))
        assertEquals("3h ago", UpdateErrorStore.timeAgo(now - 3L * 3_600_000, now))
        assertEquals("2d ago", UpdateErrorStore.timeAgo(now - 2L * 86_400_000, now))
    }
}
