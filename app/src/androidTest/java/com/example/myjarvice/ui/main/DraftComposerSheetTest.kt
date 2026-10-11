package com.example.myjarvice.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.example.myjarvice.data.*
import com.example.myjarvice.theme.MyJarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic names and callbacks only. Does not launch a picker, use models or send messages. */
class DraftComposerSheetTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun selectionIsExplicitEditableAndPreparedOnlyAfterUserAction() {
        var selections = 0
        var prepared: String? = null
        var remembered = 0
        rule.setContent { MyJarvisTheme {
            var recipient by remember { mutableStateOf("") }
            WritingDraftSheetContent(WritingProfile(), { remembered++ }, {}, { prepared = it }, recipient,
                { recipient = it }, { selections++; recipient = "Synthetic Alex" })
        } }
        assertEquals(0, selections)
        assertNull(prepared)
        rule.onNodeWithText("Choose from contacts").performScrollTo().performClick()
        rule.onNodeWithText("Who is it for? (optional)").assertTextContains("Synthetic Alex")
        assertNull(prepared)
        rule.onNodeWithText("Who is it for? (optional)").performTextReplacement("my friend")
        rule.onNodeWithText("What do you want to say?").performScrollTo().performTextInput("Say thanks")
        rule.onNodeWithText("Prepare in chat").performScrollTo().performClick()
        assertEquals(1, selections)
        assertEquals(0, remembered)
        val request = requireNotNull(prepared)
        assertTrue(request.contains("for \"my friend\""))
        assertFalse(request.contains("Synthetic Alex"))
        assertTrue(request.contains("do not send"))
    }

    @Test fun clearingNameDoesNotEraseMessageAndDoesNotRememberAContact() {
        var prepared = ""
        rule.setContent { MyJarvisTheme {
            var recipient by remember { mutableStateOf("Synthetic Alex") }
            WritingDraftSheetContent(WritingProfile(), {}, {}, { prepared = it }, recipient, { recipient = it }, {})
        } }
        rule.onNodeWithText("What do you want to say?").performScrollTo().performTextInput("Say hello")
        rule.onNodeWithContentDescription("Clear recipient").performScrollTo().performClick()
        rule.onNodeWithText("Prepare in chat").performScrollTo().performClick()
        assertFalse(prepared.contains("Synthetic Alex"))
        assertTrue(prepared.endsWith("Say hello"))
    }

    @Test fun cancelAndFailureKeepManualRecipientUsableWithLargeText() {
        var prepared = ""
        var error by mutableStateOf<String?>(null)
        rule.setContent { MyJarvisTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                var recipient by remember { mutableStateOf("my manager") }
                WritingDraftSheetContent(WritingProfile(), {}, {}, { prepared = it }, recipient, { recipient = it },
                    { error = "Synthetic picker unavailable. Type a recipient instead." }, contactError = error)
            }
        } }
        rule.onNodeWithText("Choose from contacts").performScrollTo().performClick()
        rule.onNodeWithText("Synthetic picker unavailable. Type a recipient instead.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Who is it for? (optional)").performScrollTo().assertTextContains("my manager")
        rule.runOnIdle { error = null } // Simulates a cancelled selection: no recipient update.
        rule.onNodeWithText("What do you want to say?").performScrollTo().performTextInput("Ask about tomorrow")
        rule.onNodeWithText("Prepare in chat").performScrollTo().performClick()
        assertTrue(prepared.contains("my manager"))
    }

    @Test fun busySelectionDisablesPreparationAndNameChanges() {
        var busy by mutableStateOf(false)
        rule.setContent { MyJarvisTheme {
            WritingDraftSheetContent(WritingProfile(), {}, {}, {}, "Synthetic Alex", {}, {}, contactBusy = busy)
        } }
        rule.onNodeWithText("What do you want to say?").performScrollTo().performTextInput("Hello")
        rule.runOnIdle { busy = true }
        rule.onNodeWithText("Choosing a contact…").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithContentDescription("Clear recipient").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Prepare in chat").performScrollTo().assertIsNotEnabled()
    }
}
