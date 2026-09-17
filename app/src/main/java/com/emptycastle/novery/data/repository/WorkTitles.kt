package com.emptycastle.novery.data.repository

import com.emptycastle.novery.data.local.entity.WorkProjectionEntity

/**
 * Slice-03: pure title/identity helpers for the entity layer.
 *
 * Normalization mirrors LibraryRepository's duplicate rules (bracketed
 * suffixes and punctuation dropped, case-insensitive) so suggestions stay
 * consistent with the existing duplicate finder. Fully unit-tested.
 */
object WorkTitles {

    private val BRACKETED_TEXT_REGEX = Regex("""\([^)]*\)|\[[^]]*]""")
    private val NON_TITLE_CHARACTER_REGEX = Regex("""[^a-z0-9]+""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun normalize(title: String): String {
        return title.lowercase()
            .replace(BRACKETED_TEXT_REGEX, " ")
            .replace(NON_TITLE_CHARACTER_REGEX, " ")
            .trim()
            .replace(WHITESPACE_REGEX, " ")
    }

    /** True when two titles plausibly name the same work (strict equality). */
    fun sameWork(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        return na.isNotBlank() && na == nb
    }

    /**
     * Picks the projection to open: the explicit default when still
     * attached, else the oldest attached projection. Pure — unit-tested.
     */
    fun resolveDefault(
        projections: List<WorkProjectionEntity>,
        defaultNovelUrl: String?
    ): WorkProjectionEntity? {
        if (projections.isEmpty()) return null
        if (defaultNovelUrl != null) {
            projections.find { it.novelUrl == defaultNovelUrl }?.let { return it }
        }
        return projections.minByOrNull { it.addedAt }
    }
}
