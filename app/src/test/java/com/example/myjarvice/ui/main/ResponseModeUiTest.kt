package com.example.myjarvice.ui.main

import com.example.myjarvice.data.ConnectionStatus
import com.example.myjarvice.data.SmartMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseModeUiTest {
    @Test fun automaticShowsTheRouteCurrentlyInUse() {
        val connected = responseModeUi(SmartMode.AUTO, ConnectionStatus.CONNECTED, hasLocalModel = true)
        val offline = responseModeUi(SmartMode.AUTO, ConnectionStatus.DISCONNECTED, hasLocalModel = true)

        assertEquals("Using PC now", connected.status)
        assertEquals("Using phone now", offline.status)
        assertTrue(connected.recommended)
    }

    @Test fun phoneModeHonestlyReportsMissingModel() {
        val missing = responseModeUi(SmartMode.FAST_ON_DEVICE, ConnectionStatus.DISCONNECTED, hasLocalModel = false)

        assertEquals("On this phone", missing.title)
        assertEquals("Model required", missing.status)
        assertFalse(missing.recommended)
    }

    @Test fun pcModeReportsConnectionProblemsWithoutTechnicalJargon() {
        val issue = responseModeUi(SmartMode.STRONG_HOST, ConnectionStatus.ERROR, hasLocalModel = true)

        assertEquals("Connected PC", issue.title)
        assertEquals("Connection issue", issue.status)
    }
}
