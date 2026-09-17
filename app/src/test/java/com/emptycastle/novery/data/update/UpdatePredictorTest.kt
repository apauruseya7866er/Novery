package com.emptycastle.novery.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-01.4: update-interval prediction. Pure JVM test.
 */
class UpdatePredictorTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    @Test
    fun fewerThanTwoPoints_returnsNull() {
        assertNull(UpdatePredictor.predictNextUpdate(emptyList(), now))
        assertNull(UpdatePredictor.predictNextUpdate(listOf(now - day), now))
    }

    @Test
    fun dailyCadence_predictsNextDay() {
        val points = listOf(now - 2 * day, now - day, now)
        assertEquals(now + day, UpdatePredictor.predictNextUpdate(points, now))
    }

    @Test
    fun unsortedInput_isHandled() {
        val points = listOf(now, now - 2 * day, now - day)
        assertEquals(now + day, UpdatePredictor.predictNextUpdate(points, now))
    }

    @Test
    fun evenCount_usesAverageOfMiddleTwo() {
        // Gaps: 1d, 3d -> median 2d.
        val points = listOf(now - 4 * day, now - 3 * day, now)
        assertEquals(now + 2 * day, UpdatePredictor.predictNextUpdate(points, now))
    }

    @Test
    fun tinyGaps_clampedToOneDay() {
        val hour = 60L * 60 * 1000
        val points = listOf(now - 2 * hour, now - hour, now)
        assertEquals(now + day, UpdatePredictor.predictNextUpdate(points, now))
    }

    @Test
    fun hugeGaps_clampedTo28Days() {
        val long = 100 * day
        val points = listOf(now - 2 * long, now - long, now)
        assertEquals(now + 28 * day, UpdatePredictor.predictNextUpdate(points, now))
    }

    @Test
    fun stalePattern_returnsNull() {
        // Daily novel silent for 10 days (> 3x cadence).
        val last = now - 10 * day
        val points = listOf(last - 2 * day, last - day, last)
        assertNull(UpdatePredictor.predictNextUpdate(points, now))
    }

    @Test
    fun duplicateTimestamps_ignored() {
        val points = listOf(now - day, now - day, now)
        // Only one positive gap (1d) -> predicts next day.
        assertEquals(now + day, UpdatePredictor.predictNextUpdate(points, now))
    }

    @Test
    fun expectedSoon_windowEdges() {
        val next = now + 10 * day
        assertFalse(UpdatePredictor.isExpectedSoon(next, now))
        assertTrue(UpdatePredictor.isExpectedSoon(next, next - 36 * 60 * 60 * 1000))
        assertTrue(UpdatePredictor.isExpectedSoon(next, next + 12 * 60 * 60 * 1000))
        assertFalse(UpdatePredictor.isExpectedSoon(next, next + 13 * 60 * 60 * 1000))
        assertFalse(UpdatePredictor.isExpectedSoon(next, next - 37 * 60 * 60 * 1000))
    }
}
