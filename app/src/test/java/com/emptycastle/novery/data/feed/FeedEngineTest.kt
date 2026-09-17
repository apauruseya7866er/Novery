package com.emptycastle.novery.data.feed

import com.emptycastle.novery.domain.model.FilterOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Slice-07.1a: latest-ordering resolver. Pure JVM test.
 */
class FeedEngineTest {

    @Test
    fun emptyOptions_returnsNull() {
        assertNull(FeedEngine.latestOrderBy(emptyList()))
    }

    @Test
    fun latestLabelMatchedFirst() {
        val options = listOf(
            FilterOption("Popular", "popular"),
            FilterOption("Latest Release", "latest-release-novel"),
            FilterOption("Completed", "completed")
        )
        assertEquals("latest-release-novel", FeedEngine.latestOrderBy(options))
    }

    @Test
    fun updatedValue_matched() {
        val options = listOf(
            FilterOption("Name", "name"),
            FilterOption("Latest Updates", "updated_at")
        )
        assertEquals("updated_at", FeedEngine.latestOrderBy(options))
    }

    @Test
    fun matchIsCaseInsensitive() {
        val options = listOf(
            FilterOption("TIME UPDATED", "5")
        )
        assertEquals("5", FeedEngine.latestOrderBy(options))
    }

    @Test
    fun noMatch_fallsBackToFirst() {
        val options = listOf(
            FilterOption("Popular", "popular"),
            FilterOption("Rating", "rating")
        )
        assertEquals("popular", FeedEngine.latestOrderBy(options))
    }
}
