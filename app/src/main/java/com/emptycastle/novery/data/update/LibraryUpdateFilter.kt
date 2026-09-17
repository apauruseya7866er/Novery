package com.emptycastle.novery.data.update

import com.emptycastle.novery.domain.model.ReadingStatus

/**
 * Slice-01.3: smart skip rules for scheduled library updates.
 *
 * Pure logic (no Android dependencies) — covered by unit tests.
 * Mirrors Komikku's smart-update restrictions, adapted to Novery statuses.
 */
enum class SkipReason {
    COMPLETED,
    DROPPED,
    NOT_STARTED,
    FULLY_READ
}

object LibraryUpdateFilter {

    /**
     * Returns why a novel should be skipped by the background updater,
     * or null when it should be checked.
     *
     * - COMPLETED / DROPPED: never re-check.
     * - PLAN_TO_READ: unstarted, nothing to follow yet.
     * - READING with zero unread, known chapters AND evidence of reading
     *   (a saved position): fully read. The position check matters — a
     *   never-opened novel can also sit at unread == 0 with stale counts.
     * - ON_HOLD / SPICY / READING with unread: always check.
     */
    fun shouldSkipNovel(
        status: ReadingStatus,
        unreadChapterCount: Int,
        totalChapterCount: Int,
        hasStartedReading: Boolean
    ): SkipReason? = when {
        status == ReadingStatus.COMPLETED -> SkipReason.COMPLETED
        status == ReadingStatus.DROPPED -> SkipReason.DROPPED
        status == ReadingStatus.PLAN_TO_READ -> SkipReason.NOT_STARTED
        status == ReadingStatus.READING &&
            hasStartedReading &&
            totalChapterCount > 0 &&
            unreadChapterCount <= 0 -> SkipReason.FULLY_READ
        else -> null
    }
}
