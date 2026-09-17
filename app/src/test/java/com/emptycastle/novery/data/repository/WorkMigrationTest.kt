package com.emptycastle.novery.data.repository

import com.emptycastle.novery.domain.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-04.1a: migration mapping. Pure JVM test.
 */
class WorkMigrationTest {

    private fun chapters(vararg names: String) = names.mapIndexed { i, n ->
        Chapter(name = n, url = "https://x/c$i")
    }

    @Test
    fun mapPosition_sameIndex() {
        val target = chapters("C1", "C2", "C3")
        val m = WorkMigration.mapPosition(1, target)!!
        assertEquals(1, m.newIndex)
        assertEquals("https://x/c1", m.newUrl)
        assertEquals("C2", m.newName)
    }

    @Test
    fun mapPosition_clampsToLastChapter() {
        val target = chapters("C1", "C2")
        val m = WorkMigration.mapPosition(99, target)!!
        assertEquals(1, m.newIndex)
        assertEquals("C2", m.newName)
    }

    @Test
    fun mapPosition_noPositionOrNoChapters_returnsNull() {
        val target = chapters("C1")
        assertNull(WorkMigration.mapPosition(-1, target))
        assertNull(WorkMigration.mapPosition(0, emptyList()))
    }

    @Test
    fun readMarks_firstNByOrder() {
        val target = chapters("C1", "C2", "C3", "C4")
        val marks = WorkMigration.readMarksToTransfer(2, target, true)
        assertEquals(listOf("https://x/c0", "https://x/c1"), marks.map { it.url })
    }

    @Test
    fun readMarks_disabledOrEmpty_returnsEmpty() {
        val target = chapters("C1")
        assertTrue(WorkMigration.readMarksToTransfer(5, target, false).isEmpty())
        assertTrue(WorkMigration.readMarksToTransfer(0, target, true).isEmpty())
        assertTrue(WorkMigration.readMarksToTransfer(3, emptyList(), true).isEmpty())
    }

    @Test
    fun readMarks_clampedToTargetSize() {
        val target = chapters("C1")
        assertEquals(1, WorkMigration.readMarksToTransfer(50, target, true).size)
    }

    @Test
    fun shouldMoveHistory_newerOrMissingTarget() {
        assertTrue(WorkMigration.shouldMoveHistory(2000L, null))
        assertTrue(WorkMigration.shouldMoveHistory(2000L, 1000L))
        assertFalse(WorkMigration.shouldMoveHistory(1000L, 2000L))
        assertFalse(WorkMigration.shouldMoveHistory(null, null))
        assertFalse(WorkMigration.shouldMoveHistory(null, 1000L))
    }

    @Test
    fun suggestKeepRow_furthestReadWins() {
        assertTrue(WorkMigration.suggestKeepRow(10, 2000L, 5, 1000L))
        assertFalse(WorkMigration.suggestKeepRow(5, 1000L, 10, 2000L))
    }

    @Test
    fun suggestKeepRow_tieBreaksToEarlierAdded() {
        assertTrue(WorkMigration.suggestKeepRow(7, 1000L, 7, 2000L))
        assertFalse(WorkMigration.suggestKeepRow(7, 2000L, 7, 1000L))
    }
}
