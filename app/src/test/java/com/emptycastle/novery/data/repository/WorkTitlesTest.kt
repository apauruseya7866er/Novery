package com.emptycastle.novery.data.repository

import com.emptycastle.novery.data.local.entity.WorkProjectionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-03: work title matching + default resolution. Pure JVM test.
 */
class WorkTitlesTest {

    @Test
    fun normalize_stripsBracketsAndPunctuation() {
        assertEquals("shadow slave", WorkTitles.normalize("Shadow Slave"))
        assertEquals(
            "shadow slave",
            WorkTitles.normalize("Shadow Slave (NovelFire) [Ongoing]")
        )
        assertEquals("lord of the mysteries", WorkTitles.normalize("Lord-of-the-Mysteries!"))
    }

    @Test
    fun sameWork_strictEqualityOnly() {
        assertTrue(WorkTitles.sameWork("Shadow Slave", "shadow slave (NovelFire)"))
        assertFalse(WorkTitles.sameWork("Shadow Slave", "Shadow Slave 2"))
        assertFalse(WorkTitles.sameWork("", ""))
        assertFalse(WorkTitles.sameWork("!!!", "???"))
    }

    private fun projection(url: String, addedAt: Long) = WorkProjectionEntity(
        id = 0, workId = 1, novelUrl = url, providerName = "p", addedAt = addedAt
    )

    @Test
    fun resolveDefault_prefersAttachedDefault() {
        val old = projection("a", 1000L)
        val new = projection("b", 2000L)
        assertEquals(
            "b",
            WorkTitles.resolveDefault(listOf(old, new), "b")?.novelUrl
        )
    }

    @Test
    fun resolveDefault_fallsBackToOldestWhenDefaultGone() {
        val old = projection("a", 1000L)
        val new = projection("b", 2000L)
        assertEquals(
            "a",
            WorkTitles.resolveDefault(listOf(old, new), "missing")?.novelUrl
        )
        assertEquals(
            "a",
            WorkTitles.resolveDefault(listOf(new, old), null)?.novelUrl
        )
    }

    @Test
    fun resolveDefault_empty_returnsNull() {
        assertNull(WorkTitles.resolveDefault(emptyList(), "a"))
    }
}
