package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test

class ClockActionParametersTest {
    @Test fun alarmHandles24HourNoonAndMidnight() {
        assertEquals(ClockActionParameters.Alarm(12, 30), ClockActionParameters.alarm("12:30"))
        assertEquals(ClockActionParameters.Alarm(0, 0), ClockActionParameters.alarm("12 AM"))
        assertEquals(ClockActionParameters.Alarm(12, 0), ClockActionParameters.alarm("12 PM"))
        assertEquals(ClockActionParameters.Alarm(19, 15), ClockActionParameters.alarm("7:15 pm"))
    }

    @Test fun alarmNeverGuessesMissingOrInvalidTime() {
        listOf("tomorrow", "7", "25:00", "00 PM", "7:99 AM", "07:00; run command").forEach {
            assertTrue(it, runCatching { ClockActionParameters.alarm(it) }.isFailure)
        }
    }

    @Test fun timerRequiresUnitAndValidWholeSeconds() {
        assertEquals(600, ClockActionParameters.timerSeconds("10 minutes"))
        assertEquals(5400, ClockActionParameters.timerSeconds("1.5 hours"))
        assertEquals(30, ClockActionParameters.timerSeconds("30s"))
        listOf("ten minutes", "10", "0 seconds", "25 hours", "0.5 seconds", "1 hour 30 minutes").forEach {
            assertTrue(it, runCatching { ClockActionParameters.timerSeconds(it) }.isFailure)
        }
    }
}
