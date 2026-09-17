package com.emptycastle.novery.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-05.4: tracker start/update/finish. Pure JVM test.
 */
class BackgroundWorkTrackerTest {

    @Test
    fun startUpdateFinish_cycle() {
        BackgroundWorkTracker.clear()
        assertTrue(BackgroundWorkTracker.works.value.isEmpty())

        BackgroundWorkTracker.start(
            BackgroundWorkTracker.ID_LIBRARY_UPDATE,
            "Checking library"
        )
        assertEquals(
            "Checking library",
            BackgroundWorkTracker.works.value[BackgroundWorkTracker.ID_LIBRARY_UPDATE]?.label
        )

        BackgroundWorkTracker.update(BackgroundWorkTracker.ID_LIBRARY_UPDATE, "2/10 · Foo")
        assertEquals(
            "2/10 · Foo",
            BackgroundWorkTracker.works.value[BackgroundWorkTracker.ID_LIBRARY_UPDATE]?.detail
        )

        BackgroundWorkTracker.finish(BackgroundWorkTracker.ID_LIBRARY_UPDATE)
        assertTrue(BackgroundWorkTracker.works.value.isEmpty())
        assertNull(BackgroundWorkTracker.works.value["missing"])
    }

    @Test
    fun updateMissing_isNoOp() {
        BackgroundWorkTracker.clear()
        BackgroundWorkTracker.update("nope", "x")
        assertTrue(BackgroundWorkTracker.works.value.isEmpty())
    }

    @Test
    fun multipleWorks_coexist() {
        BackgroundWorkTracker.clear()
        BackgroundWorkTracker.start("a", "A")
        BackgroundWorkTracker.start("b", "B")
        assertEquals(2, BackgroundWorkTracker.works.value.size)
        BackgroundWorkTracker.finish("a")
        assertEquals(listOf("b"), BackgroundWorkTracker.works.value.keys.toList())
        BackgroundWorkTracker.clear()
    }
}
