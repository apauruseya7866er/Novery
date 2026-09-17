package com.emptycastle.novery.data.feed

import com.emptycastle.novery.domain.model.FilterOption
import com.emptycastle.novery.provider.MainProvider

/**
 * Slice-07.1a: pure helpers for the Latest feed.
 * Unit-tested; no Android dependencies.
 */
object FeedEngine {

    /**
     * Picks the provider's "latest updates" ordering for the feed.
     * Matches latest/updated/newest/recent in label or value, else falls
     * back to the provider default (null = default listing).
     */
    fun latestOrderBy(provider: MainProvider): String? {
        val options = provider.orderBys
        if (options.isEmpty()) return null
        return latestOrderBy(options)
    }

    fun latestOrderBy(options: List<FilterOption>): String? {
        if (options.isEmpty()) return null
        val match = options.firstOrNull { option ->
            val haystack = (option.label + " " + option.value).lowercase()
            "latest" in haystack || "updated" in haystack ||
                "newest" in haystack || "recent" in haystack
        }
        return (match ?: options.first()).value
    }

    /** Max novels kept per provider section. */
    const val SECTION_LIMIT = 10
}
