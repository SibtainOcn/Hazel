package com.hazel.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every placeholder takes its phase from the frame time, so separate loops stay in step. */
class LoadingClockTest {

    @Test
    fun `one cycle runs at the calm pace`() {
        assertEquals(0f, phaseAt(0L), 0f)
        assertEquals(0.5f, phaseAt(LOADING_CYCLE_MS / 2), 0.001f)
        assertEquals(0f, phaseAt(LOADING_CYCLE_MS), 0f)
        assertEquals(phaseAt(123L), phaseAt(123L + LOADING_CYCLE_MS * 40), 0f)
    }

    @Test
    fun `the cycle is in the slow range`() {
        assertEquals(true, LOADING_CYCLE_MS in 1600L..2000L)
    }
}
