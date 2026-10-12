package com.example.myjarvice.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.myjarvice.data.JarvisMessage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic feed only; does not approve sharing or write any Activity records. */
class ReplyReviewEntryTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun completedReplyOpensItsOwnTextWithoutCopyingOrSpeaking() {
        var copies = 0
        var speech = 0
        rule.setContent { MaterialTheme {
            ChatFeed(listOf(JarvisMessage("Jarvis (On-device)", "Synthetic completed reply")), false,
                { copies++ }, { speech++ }, Modifier.fillMaxSize())
        } }
        rule.onNodeWithText("Review & share").performScrollTo().performClick()
        rule.onNodeWithText("Text to share").assertTextContains("Synthetic completed reply")
        assertEquals(0, copies); assertEquals(0, speech)
        rule.onNodeWithContentDescription("Close reply review").performClick()
        rule.onNodeWithText("Text to share").assertDoesNotExist()
    }

    @Test fun streamingReplyDoesNotOfferReviewOrShare() {
        rule.setContent { MaterialTheme {
            ChatFeed(listOf(JarvisMessage("Jarvis", "Synthetic streaming reply", "PARTIAL")), true,
                {}, {}, Modifier.fillMaxSize())
        } }
        rule.onNodeWithText("Review & share").assertDoesNotExist()
        rule.onNodeWithText("Copy").assertDoesNotExist()
    }

    @Test fun errorReplyDoesNotOfferReviewOrShare() {
        rule.setContent { MaterialTheme {
            ChatFeed(listOf(JarvisMessage("Jarvis", "Synthetic error", "ERROR")), false,
                {}, {}, Modifier.fillMaxSize())
        } }
        rule.onNodeWithText("Review & share").assertDoesNotExist()
        rule.onNodeWithText("Copy").assertExists()
    }
}
