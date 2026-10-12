package com.example.myjarvice.ui.main

import android.content.Intent
import android.os.Build
import org.junit.Assert.*
import org.junit.Test

/** Only inspects synthetic intents; never launches an app or the system share sheet. */
class ReplyShareIntentTest {
    @Test fun chooserContainsOnlyTheExactApprovedPlainTextWithoutRecipientsOrAttachments() {
        val body = "  Synthetic edited reply\n🦉  "
        val chooser = replyShareChooser(body)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = if (Build.VERSION.SDK_INT >= 33) chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else @Suppress("DEPRECATION") chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(send)
        requireNotNull(send)
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals(body, send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(setOf(Intent.EXTRA_TEXT), send.extras!!.keySet())
        assertNull(send.`package`); assertNull(send.component); assertNull(send.data); assertNull(send.clipData)
        assertEquals(0, send.flags)
    }

    @Test(expected = IllegalArgumentException::class) fun oversizedTextIsNotSilentlyTruncated() {
        replyShareChooser("x".repeat(20_001))
    }
}
