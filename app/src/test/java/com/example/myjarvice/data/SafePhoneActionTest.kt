package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test

class SafePhoneActionTest {
    @Test fun validatesModelSelectedLowRiskActions() {
        assertEquals(SafePhoneAction("DEVICE_STATUS", "", false), PhoneActionPolicy.validate("device_status", ""))
        assertEquals(SafePhoneAction("FLASHLIGHT", "on", false), PhoneActionPolicy.validate("FLASHLIGHT", "on"))
        assertEquals(SafePhoneAction("OPEN_APP", "YouTube", false), PhoneActionPolicy.validate("OPEN_APP", "YouTube"))
        assertEquals(SafePhoneAction("NAVIGATE", "Central Park", false), PhoneActionPolicy.validate("NAVIGATE", "Central Park"))
    }

    @Test fun riskyActionsRequireConfirmation() {
        assertTrue(PhoneActionPolicy.validate("CALL", "Mom")!!.requiresConfirmation)
        assertTrue(PhoneActionPolicy.validate("WHATSAPP", "I am on my way")!!.requiresConfirmation)
    }

    @Test fun policyRejectsUnknownMalformedAndUnboundedModelOutput() {
        assertNull(PhoneActionPolicy.validate("SHELL", "anything"))
        assertNull(PhoneActionPolicy.validate("DEVICE_STATUS", "unexpected"))
        assertNull(PhoneActionPolicy.validate("OPEN_APP", "x".repeat(300)))
        assertNull(PhoneActionPolicy.validate("FLASHLIGHT", "destroy"))
        assertNull(PhoneActionPolicy.validate("OPEN_APP", "You\u0000Tube"))
        assertNull(PhoneActionPolicy.validate("SET_ALARM", "tomorrow"))
        assertNull(PhoneActionPolicy.validate("SET_TIMER", "10"))
        assertNotNull(PhoneActionPolicy.validate("SET_ALARM", "7:30 PM"))
        assertNotNull(PhoneActionPolicy.validate("SET_TIMER", "10 minutes"))
    }
}
