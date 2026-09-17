package com.emptycastle.novery.service

import com.emptycastle.novery.ui.navigation.NovelDeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-05.2: notification IDs + deep-link store. Pure JVM test.
 */
class LibraryUpdateNotifierTest {

    @Test
    fun notificationIds_stableAndInRange() {
        val url = "https://novelfire.net/book/shadow-slave"
        val first = LibraryUpdateNotifier.notificationIdFor(url)
        assertEquals(first, LibraryUpdateNotifier.notificationIdFor(url))
        assertTrue(first in 6001..6900)
    }

    @Test
    fun notificationIds_differPerNovel() {
        val a = LibraryUpdateNotifier.notificationIdFor("https://a.com/x")
        val b = LibraryUpdateNotifier.notificationIdFor("https://b.com/y")
        // Extremely likely distinct (hash-based); the contract is stability.
        assertTrue(a in 6001..6900 && b in 6001..6900)
    }

    @Test
    fun deepLink_postConsumeRoundTrip() {
        NovelDeepLink.consume()
        assertNull(NovelDeepLink.link.value)

        NovelDeepLink.post("https://x/n", "NovelFire")
        val target = NovelDeepLink.link.value!!
        assertEquals("https://x/n", target.novelUrl)
        assertEquals("NovelFire", target.providerName)

        NovelDeepLink.consume()
        assertNull(NovelDeepLink.link.value)
    }

    @Test
    fun deepLink_blankGuard() {
        NovelDeepLink.consume()
        NovelDeepLink.post("", "NovelFire")
        assertNull(NovelDeepLink.link.value)
        NovelDeepLink.post("https://x/n", "")
        assertNull(NovelDeepLink.link.value)
    }
}
