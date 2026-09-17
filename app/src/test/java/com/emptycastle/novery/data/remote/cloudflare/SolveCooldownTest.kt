package com.emptycastle.novery.data.remote.cloudflare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-02.2: solver cooldown. Pure JVM test with a fake clock.
 */
class SolveCooldownTest {

    @Test
    fun freshHost_isNotCooling() {
        val cd = SolveCooldown(120_000L) { 0L }
        assertFalse(cd.isCooling("example.com"))
        assertEquals(0L, cd.remainingMs("example.com"))
    }

    @Test
    fun failure_coolsHostForConfiguredWindow() {
        var now = 1_000_000L
        val cd = SolveCooldown(120_000L) { now }
        cd.recordFailure("example.com")

        assertTrue(cd.isCooling("example.com"))
        assertEquals(120_000L, cd.remainingMs("example.com"))

        now += 119_999L
        assertTrue(cd.isCooling("example.com"))
        assertEquals(1L, cd.remainingMs("example.com"))

        now += 1L
        assertFalse(cd.isCooling("example.com"))
        assertEquals(0L, cd.remainingMs("example.com"))
    }

    @Test
    fun success_clearsCooldown() {
        var now = 1_000_000L
        val cd = SolveCooldown(120_000L) { now }
        cd.recordFailure("example.com")
        assertTrue(cd.isCooling("example.com"))

        cd.recordSuccess("example.com")
        assertFalse(cd.isCooling("example.com"))
    }

    @Test
    fun cooldown_isPerHost() {
        var now = 1_000_000L
        val cd = SolveCooldown(120_000L) { now }
        cd.recordFailure("a.com")

        assertTrue(cd.isCooling("a.com"))
        assertFalse(cd.isCooling("b.com"))
    }
}
