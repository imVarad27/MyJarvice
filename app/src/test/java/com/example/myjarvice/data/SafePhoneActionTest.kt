package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test

class SafePhoneActionTest {
    @Test fun recognizesLowRiskActions() {
        assertEquals(SafePhoneAction("DEVICE_STATUS", "", false), SafePhoneActionParser.parse("what is my battery?"))
        assertEquals(SafePhoneAction("FLASHLIGHT", "on", false), SafePhoneActionParser.parse("turn the flashlight on"))
        assertEquals(SafePhoneAction("OPEN_APP", "YouTube", false), SafePhoneActionParser.parse("open YouTube"))
        assertEquals(SafePhoneAction("NAVIGATE", "Central Park", false), SafePhoneActionParser.parse("directions to Central Park"))
        assertEquals(SafePhoneAction("SET_ALARM", "7 PM", false), SafePhoneActionParser.parse("set an alarm for 7 PM"))
        assertEquals(SafePhoneAction("SET_TIMER", "10 minutes", false), SafePhoneActionParser.parse("start a timer for 10 minutes"))
    }

    @Test fun riskyActionsRequireConfirmation() {
        assertTrue(SafePhoneActionParser.parse("call Mom")!!.requiresConfirmation)
        assertEquals("WHATSAPP", SafePhoneActionParser.parse("send a WhatsApp message I am on my way")!!.type)
        assertFalse(SafePhoneActionParser.parse("send an email to Mom")?.requiresConfirmation ?: false)
    }

    @Test fun parserDoesNotTreatNormalQuestionsOrUnboundedInputAsActions() {
        assertNull(SafePhoneActionParser.parse("Why is the sky blue?"))
        assertNull(SafePhoneActionParser.parse("open " + "x".repeat(300)))
        assertNull(SafePhoneActionParser.parse("turn the flashlight off and then call Mom"))
    }
}
