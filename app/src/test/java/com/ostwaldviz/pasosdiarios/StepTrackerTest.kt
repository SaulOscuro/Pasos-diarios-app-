package com.ostwaldviz.pasosdiarios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StepTrackerTest {
    @Test
    fun startsAtZero() {
        assertEquals(0, StepTracker.steps(0))
        assertEquals(0, StepTracker.completedCount(0))
        assertEquals(0f, StepTracker.progress(0))
    }

    @Test
    fun eachBlockIsIndependentlyToggleable() {
        val first = StepTracker.toggle(0, 0)
        val firstAndLast = StepTracker.toggle(first, 9)
        assertTrue(StepTracker.isCompleted(firstAndLast, 0))
        assertTrue(StepTracker.isCompleted(firstAndLast, 9))
        assertFalse(StepTracker.isCompleted(firstAndLast, 1))
        assertEquals(2_000, StepTracker.steps(firstAndLast))
        assertEquals(first, StepTracker.toggle(firstAndLast, 9))
    }

    @Test
    fun allTenBlocksEqualTenThousand() {
        val full = (0 until 10).fold(0) { mask, index ->
            StepTracker.toggle(mask, index)
        }
        assertEquals(10, StepTracker.completedCount(full))
        assertEquals(10_000, StepTracker.steps(full))
        assertEquals(1f, StepTracker.progress(full))
    }

    @Test
    fun extraBitsAreIgnored() {
        assertEquals(0, StepTracker.normalized(1 shl 12))
        assertEquals(0, StepTracker.steps(1 shl 12))
    }

    @Test
    fun invalidBlocksAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StepTracker.toggle(0, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StepTracker.isCompleted(0, 10)
        }
    }
}
