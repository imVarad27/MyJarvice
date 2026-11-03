package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test

class SafePhoneActionTest {
    @Test fun validatesModelSelectedLowRiskActions() {
        assertEquals(SafePhoneAction("DEVICE_STATUS", "", false), PhoneActionPolicy.validate("device_status", ""))
        assertEquals(SafePhoneAction("FLASHLIGHT", "on", false), PhoneActionPolicy.validate("FLASHLIGHT", "on"))
        assertEquals(SafePhoneAction("OPEN_APP", "YouTube", false), PhoneActionPolicy.validate("OPEN_APP", "YouTube"))
        assertEquals(SafePhoneAction("NAVIGATE", "Central Park", false), PhoneActionPolicy.validate("NAVIGATE", "Central Park"))
        assertEquals(SafePhoneAction("ADJUST_VOLUME", "50%", false), PhoneActionPolicy.validate("ADJUST_VOLUME", "50%"))
        assertEquals(SafePhoneAction("OPEN_SETTINGS", "wifi", false), PhoneActionPolicy.validate("OPEN_SETTINGS", "wifi"))
        assertEquals(SafePhoneAction("READ_CLIPBOARD", "", false), PhoneActionPolicy.validate("READ_CLIPBOARD", ""))
        assertEquals(SafePhoneAction("COPY_CLIPBOARD", "hello world", false), PhoneActionPolicy.validate("COPY_CLIPBOARD", "hello world"))
        assertEquals(SafePhoneAction("OPEN_URL", "https://google.com", false), PhoneActionPolicy.validate("OPEN_URL", "https://google.com"))
        assertEquals(SafePhoneAction("OPEN_CAMERA", "", false), PhoneActionPolicy.validate("OPEN_CAMERA", ""))
        assertEquals(SafePhoneAction("SEARCH_PLACES", "coffee shops", false), PhoneActionPolicy.validate("SEARCH_PLACES", "coffee shops"))
        assertEquals(SafePhoneAction("SHOW_ALARMS", "", false), PhoneActionPolicy.validate("SHOW_ALARMS", ""))
        assertEquals(SafePhoneAction("SHOW_TIMERS", "", false), PhoneActionPolicy.validate("SHOW_TIMERS", ""))
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
