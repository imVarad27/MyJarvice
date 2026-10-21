package com.example.myjarvice.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RoutinePlanTest {
    @Test fun `steps are trimmed and blank lines are ignored`() {
        assertEquals(listOf("Open notes", "Choose top priority"), RoutinePlan.normalizedSteps(listOf(" Open notes ", " ", "Choose top priority")))
    }

    @Test fun `routine needs at least one useful step`() {
        assertThrows(IllegalArgumentException::class.java) { RoutinePlan.validate("Morning reset", emptyList()) }
    }

    @Test fun `routine title and step limits are enforced`() {
        assertThrows(IllegalArgumentException::class.java) { RoutinePlan.validate(" ", listOf("Step")) }
        assertThrows(IllegalArgumentException::class.java) { RoutinePlan.validate("Routine", List(RoutinePlan.MAX_STEPS + 1) { "Step $it" }) }
    }
}
