package com.emptycastle.novery.ui.screens.home.tabs.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Slice-02.4: cooldown message formatting. Pure JVM test.
 */
class FormatCooldownTest {

    @Test
    fun zeroOrNegative_returnsNull() {
        assertNull(formatCooldownMs(0L))
        assertNull(formatCooldownMs(-500L))
    }

    @Test
    fun underAMinute_showsSeconds() {
        assertEquals("1s", formatCooldownMs(1L))
        assertEquals("45s", formatCooldownMs(45_000L))
        // Rounds up partial seconds.
        assertEquals("2s", formatCooldownMs(1_001L))
    }

    @Test
    fun overAMinute_showsMinutesAndSeconds() {
        assertEquals("1m 00s", formatCooldownMs(60_000L))
        assertEquals("2m 05s", formatCooldownMs(125_000L))
        assertEquals("2m 00s", formatCooldownMs(119_500L))
    }
}
