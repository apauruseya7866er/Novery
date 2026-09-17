package com.emptycastle.novery.data.repository

import com.emptycastle.novery.domain.model.Chapter

/**
 * Slice-04.1a: pure migration mapping logic.
 *
 * Chapter URLs are source-specific, so cross-source moves map by chapter
 * INDEX (clamped), never by URL. Fully unit-tested; the transactional
 * application lives in WorkRepository.
 */
object WorkMigration {

    /**
     * Where the reader left off, mapped onto the target chapter list.
     * Null when there is no position ([oldIndex] < 0) or no chapters.
     */
    data class PositionMapping(
        val oldIndex: Int,
        val newIndex: Int,
        val newUrl: String,
        val newName: String
    )

    fun mapPosition(oldIndex: Int, targetChapters: List<Chapter>): PositionMapping? {
        if (oldIndex < 0 || targetChapters.isEmpty()) return null
        val newIndex = oldIndex.coerceIn(0, targetChapters.lastIndex)
        val chapter = targetChapters[newIndex]
        return PositionMapping(oldIndex, newIndex, chapter.url, chapter.name)
    }

    /**
     * Read checkmarks to recreate on the target, matched by order:
     * the first [oldReadCount] target chapters. Empty when disabled or
     * when there is nothing to map.
     */
    fun readMarksToTransfer(
        oldReadCount: Int,
        targetChapters: List<Chapter>,
        enabled: Boolean
    ): List<Chapter> {
        if (!enabled || oldReadCount <= 0 || targetChapters.isEmpty()) return emptyList()
        return targetChapters.take(oldReadCount.coerceAtMost(targetChapters.size))
    }

    /**
     * Whether the source history entry should move: only when the target
     * has none, or the source one is newer. Timestamps in millis, null =
     * absent.
     */
    fun shouldMoveHistory(sourceTimestamp: Long?, targetTimestamp: Long?): Boolean {
        if (sourceTimestamp == null) return false
        if (targetTimestamp == null) return true
        return sourceTimestamp > targetTimestamp
    }

    /**
     * Slice-04.2: suggests which library row should survive a merge.
     * Furthest read wins; ties break toward the earlier-added entry.
     * @return true to keep A, false to keep B. Pure — unit-tested.
     */
    fun suggestKeepRow(
        aLastReadIndex: Int,
        aAddedAt: Long,
        bLastReadIndex: Int,
        bAddedAt: Long
    ): Boolean {
        if (aLastReadIndex != bLastReadIndex) {
            return aLastReadIndex > bLastReadIndex
        }
        return aAddedAt <= bAddedAt
    }
}
