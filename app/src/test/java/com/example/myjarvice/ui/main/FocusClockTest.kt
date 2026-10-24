package com.example.myjarvice.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusClockTest {
    @Test fun `focus clock bounds remaining time and calculates progress`() {
        val end = FocusClock.endAt(startedAt = 1_000, durationMinutes = 25)
        assertEquals(1_500_000L, FocusClock.remainingMillis(end, 1_000))
        assertEquals(0L, FocusClock.remainingMillis(end, end + 1))
        assertEquals(.5f, FocusClock.progress(750_000L, 25))
        assertTrue(FocusClock.progress(-1, 25) <= 1f)
    }

    @Test fun `clock label rounds partial seconds up`() {
        assertEquals("00:01", FocusClock.label(1))
        assertEquals("25:00", FocusClock.label(1_500_000L))
    }
}
