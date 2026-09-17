package com.emptycastle.novery.data.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-07.1b: saved-search list helpers. Pure JVM test.
 */
class SavedSearchesTest {

    @Test
    fun normalize_trimsAndCollapses() {
        assertEquals("Shadow Slave", SavedSearches.normalize("  Shadow   Slave  "))
        assertNull(SavedSearches.normalize("   "))
    }

    @Test
    fun add_dedupesCaseInsensitivelyNewestFirst() {
        val once = SavedSearches.add(emptyList(), "Shadow Slave")
        assertEquals(1, once.size)
        val twice = SavedSearches.add(once, "shadow slave")
        assertEquals(1, twice.size)
        assertEquals("shadow slave", twice.first().query)
    }

    @Test
    fun add_blankIsNoOp() {
        assertTrue(SavedSearches.add(emptyList(), "  ").isEmpty())
    }

    @Test
    fun add_capsSize() {
        var list = emptyList<SavedSearch>()
        repeat(25) { i -> list = SavedSearches.add(list, "q$i") }
        assertEquals(20, list.size)
        assertEquals("q24", list.first().query)
    }

    @Test
    fun remove_byId() {
        val list = SavedSearches.add(emptyList(), "abc")
        val id = list.first().id
        assertTrue(SavedSearches.remove(list, id).isEmpty())
        assertEquals(1, SavedSearches.remove(list, "nope").size)
    }
}
