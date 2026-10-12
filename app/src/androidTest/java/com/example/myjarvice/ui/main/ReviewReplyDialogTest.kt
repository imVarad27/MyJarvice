package com.example.myjarvice.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.example.myjarvice.theme.MyJarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic callbacks only: no contacts, models, clipboard, journal, or external intent launch. */
class ReviewReplyDialogTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun openingEditingAndPreviewDoNotShareUntilExactTextIsApproved() {
        val handoffs = mutableListOf<String>()
        val outcomes = mutableListOf<String>()
        rule.setContent { MyJarvisTheme {
            ReviewReplyContent("Original synthetic reply", {}, { handoffs += it; true }, outcomes::add)
        } }
        assertTrue(handoffs.isEmpty()); assertTrue(outcomes.isEmpty())
        rule.onNodeWithText("Text to share").performScrollTo().performTextReplacement("Edited synthetic reply")
        rule.onNodeWithText("Review sharing").performClick()
        rule.onNodeWithText("Share this exact text?").assertIsDisplayed()
        rule.onNode(hasText("Edited synthetic reply") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        assertTrue(handoffs.isEmpty())
        rule.onNodeWithText("Approve & open share sheet").performClick()
        assertEquals(listOf("Edited synthetic reply"), handoffs)
        assertEquals(listOf("awaiting_approval", "approved", "started"), outcomes)
        rule.onNodeWithText("Share sheet opened. Jarvis cannot confirm whether you selected an app or sent anything.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun cancelledPreviewAllowsEditsAndRequiresFreshApproval() {
        var handedOff: String? = null
        rule.setContent { MyJarvisTheme { ReviewReplyContent("Original synthetic reply", {}, { handedOff = it; true }) } }
        rule.onNodeWithText("Review sharing").performClick()
        rule.onNodeWithText("Keep editing").performClick()
        assertNull(handedOff)
        rule.onNodeWithText("Text to share").performScrollTo().performTextReplacement("A different reply")
        rule.onNodeWithText("Review sharing").performClick()
        assertNull(handedOff)
        rule.onNodeWithText("Approve & open share sheet").performClick()
        assertEquals("A different reply", handedOff)
    }

    @Test fun failedLaunchKeepsEditedTextAndRetryRequiresAnotherApproval() {
        var attempts = 0
        val outcomes = mutableListOf<String>()
        rule.setContent { MyJarvisTheme {
            ReviewReplyContent("Synthetic reply", {}, { attempts++; false }, outcomes::add)
        } }
        rule.onNodeWithText("Text to share").performScrollTo().performTextReplacement("Keep these edits")
        rule.onNodeWithText("Review sharing").performClick()
        rule.onNodeWithText("Approve & open share sheet").performClick()
        rule.onNodeWithText("Text to share").assertTextContains("Keep these edits")
        rule.onNodeWithText("Couldn't open the share sheet. Your edits are still here. Review again to retry.")
            .performScrollTo().assertIsDisplayed()
        assertEquals(1, attempts)
        assertEquals("failed", outcomes.last())
        rule.onNodeWithText("Review sharing").performClick()
        assertEquals(1, attempts)
        rule.onNodeWithText("Keep editing").performClick()
        assertEquals(1, attempts)
    }

    @Test fun blankAndOversizedEditsCannotBeSharedAndAreNotTruncated() {
        rule.setContent { MyJarvisTheme { ReviewReplyContent("Synthetic reply", {}, { fail("Invalid text shared"); false }) } }
        rule.onNodeWithText("Text to share").performScrollTo().performTextReplacement(" ")
        rule.onNodeWithText("Review sharing").assertIsNotEnabled()
        rule.onNodeWithText("Text to share").performTextReplacement("x".repeat(20_001))
        rule.onNodeWithText("Text to share").assertTextContains(" ")
        rule.onNodeWithText("Review sharing").assertIsNotEnabled()
        rule.onNodeWithText("Text is too long. That change wasn't applied. Keep it within 20,000 characters.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun closingAnEditedCopyRequiresDiscardApprovalAtLargeFont() {
        var closed = 0
        var closeRequest by mutableIntStateOf(0)
        rule.setContent { MyJarvisTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                ReviewReplyContent("Synthetic reply", { closed++ }, { false }, closeRequest = closeRequest)
            }
        } }
        rule.onNodeWithText("Text to share").performScrollTo().performTextReplacement("Unsaved edits")
        rule.runOnIdle { closeRequest++ } // Native dialog Back/outside dismissal takes this same path.
        rule.onNodeWithText("Discard your edits?").assertIsDisplayed()
        assertEquals(0, closed)
        rule.onNodeWithText("Keep editing").performClick()
        rule.onNodeWithContentDescription("Close reply review").performClick()
        rule.onNodeWithText("Discard edits").performClick()
        assertEquals(1, closed)
    }
}
