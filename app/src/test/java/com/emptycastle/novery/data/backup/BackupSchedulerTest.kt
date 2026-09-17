package com.emptycastle.novery.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-06.2: backup prune selection + labels. Pure JVM test.
 */
class BackupSchedulerTest {

    @Test
    fun selectDeletions_keepsNewest() {
        val names = listOf(
            "novery_backup_2026-09-14_1000.novery",
            "novery_backup_2026-09-15_1000.novery",
            "novery_backup_2026-09-16_1000.novery",
            "novery_backup_2026-09-17_1000.novery",
            "novery_backup_2026-09-18_1000.novery"
        )
        val victims = BackupScheduler.selectDeletions(names, 4)
        assertEquals(
            listOf("novery_backup_2026-09-14_1000.novery"),
            victims
        )
    }

    @Test
    fun selectDeletions_underLimit_deletesNothing() {
        assertTrue(BackupScheduler.selectDeletions(listOf("a", "b"), 4).isEmpty())
        assertTrue(BackupScheduler.selectDeletions(emptyList(), 4).isEmpty())
    }

    @Test
    fun intervalLabels_coverSupported() {
        for (h in BackupScheduler.SUPPORTED_INTERVALS_HOURS) {
            assertTrue(BackupScheduler.intervalLabel(h).isNotBlank())
        }
        assertEquals("Every 24 hours", BackupScheduler.intervalLabel(24L))
        assertEquals("Every week", BackupScheduler.intervalLabel(168L))
    }
}
