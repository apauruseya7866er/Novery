package com.emptycastle.novery.data.update

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-01.2: prefs → WorkManager constraint mapping.
 * Pure JVM test — no Android framework needed.
 */
class LibraryUpdateSchedulerTest {

    @Test
    fun wifiOnly_mapsToUnmetered() {
        val c = LibraryUpdateScheduler.buildConstraints(
            wifiOnly = true,
            requireCharging = false
        )
        assertEquals(NetworkType.UNMETERED, c.requiredNetworkType)
        assertFalse(c.requiresCharging())
        assertTrue(c.requiresBatteryNotLow())
    }

    @Test
    fun wifiOff_mapsToConnected() {
        val c = LibraryUpdateScheduler.buildConstraints(
            wifiOnly = false,
            requireCharging = false
        )
        assertEquals(NetworkType.CONNECTED, c.requiredNetworkType)
        assertFalse(c.requiresCharging())
    }

    @Test
    fun requireCharging_isPropagated() {
        val c = LibraryUpdateScheduler.buildConstraints(
            wifiOnly = true,
            requireCharging = true
        )
        assertEquals(NetworkType.UNMETERED, c.requiredNetworkType)
        assertTrue(c.requiresCharging())
    }

    @Test
    fun allCombinations_keepBatteryGuard() {
        for (wifi in listOf(true, false)) {
            for (charging in listOf(true, false)) {
                assertTrue(
                    "battery guard lost for wifi=$wifi charging=$charging",
                    LibraryUpdateScheduler.buildConstraints(wifi, charging)
                        .requiresBatteryNotLow()
                )
            }
        }
    }

    @Test
    fun intervalLabels_coverSupportedIntervals() {
        for (h in LibraryUpdateScheduler.SUPPORTED_INTERVALS_HOURS) {
            val label = LibraryUpdateScheduler.intervalLabel(h)
            assertTrue(label.isNotBlank())
        }
        assertEquals("Every 12 hours", LibraryUpdateScheduler.intervalLabel(12L))
        assertEquals("Every week", LibraryUpdateScheduler.intervalLabel(168L))
    }
}
