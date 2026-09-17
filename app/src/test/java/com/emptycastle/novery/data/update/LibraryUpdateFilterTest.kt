package com.emptycastle.novery.data.update

import com.emptycastle.novery.domain.model.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Slice-01.3: smart skip rules. Pure JVM test.
 */
class LibraryUpdateFilterTest {

    @Test
    fun completed_isSkipped() {
        assertEquals(
            SkipReason.COMPLETED,
            LibraryUpdateFilter.shouldSkipNovel(ReadingStatus.COMPLETED, 5, 100, true)
        )
    }

    @Test
    fun dropped_isSkipped() {
        assertEquals(
            SkipReason.DROPPED,
            LibraryUpdateFilter.shouldSkipNovel(ReadingStatus.DROPPED, 50, 100, true)
        )
    }

    @Test
    fun planToRead_isSkippedAsNotStarted() {
        assertEquals(
            SkipReason.NOT_STARTED,
            LibraryUpdateFilter.shouldSkipNovel(ReadingStatus.PLAN_TO_READ, 100, 100, false)
        )
    }

    @Test
    fun readingFullyRead_isSkipped() {
        assertEquals(
            SkipReason.FULLY_READ,
            LibraryUpdateFilter.shouldSkipNovel(ReadingStatus.READING, 0, 3186, true)
        )
    }

    @Test
    fun readingNeverOpened_isChecked_despiteZeroUnread() {
        // Regression: a never-opened novel can sit at unread == 0 with
        // stale counts (verified live on emulator). Must still be checked.
        assertNull(
            LibraryUpdateFilter.shouldSkipNovel(ReadingStatus.READING, 0, 3186, false)
        )
    }

    @Test
    fun readingWithUnread_isChecked() {
        assertNull(
            LibraryUpdateFilter.shouldSkipNovel(ReadingStatus.READING, 3, 3186, true)
        )
    }

    @Test
    fun readingUnknownCounts_isChecked() {
        // totalChapterCount == 0 means "unknown" — never skip on that basis.
        assertNull(
            LibraryUpdateFilter.shouldSkipNovel(ReadingStatus.READING, 0, 0, true)
        )
    }

    @Test
    fun onHoldAndSpicy_areChecked() {
        assertNull(
            LibraryUpdateFilter.shouldSkipNovel(ReadingStatus.ON_HOLD, 0, 50, true)
        )
        assertNull(
            LibraryUpdateFilter.shouldSkipNovel(ReadingStatus.SPICY, 2, 50, true)
        )
    }
}
