package com.example.myjarvice.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.myjarvice.data.RememberItem
import com.example.myjarvice.data.RememberKind
import com.example.myjarvice.data.TodayBrief
import com.example.myjarvice.data.CalendarAgendaItem
import com.example.myjarvice.data.WritingProfile
import com.example.myjarvice.data.DraftTone
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Tests use synthetic UI data and never access the user's inbox or chat history. */
class MainScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun todayReminderOpensTheSelectedSavedItem() {
        val now = System.currentTimeMillis()
        val reminder = RememberItem("demo-reminder", RememberKind.TEXT, "Review the sketch", "", "", now, reminderAt = now)
        var opened: String? = null
        compose.setContent {
            MaterialTheme {
                TodayHomeContent(TodayBrief.from(listOf(reminder), now), now, onOpenSaved = { opened = it })
            }
        }
        compose.onAllNodesWithText("Review the sketch")[0].performClick()
        compose.runOnIdle { assertEquals("demo-reminder", opened) }
    }

    @Test fun draftHelperPreparesTextOnlyAfterTheUserProvidesAnIntent() {
        var prepared: String? = null
        compose.setContent { MaterialTheme { WritingDraftSheetContent(WritingProfile(), {}, {}, { prepared = it }) } }
        compose.onNodeWithText("Prepare in chat").assertIsNotEnabled()
        compose.onNodeWithText("What do you want to say?").performTextInput("Ask to reschedule our meeting")
        compose.onNodeWithText("Professional").performScrollTo().performClick()
        compose.onNodeWithText("Prepare in chat").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(prepared.orEmpty().contains("Professional"))
            assertTrue(prepared.orEmpty().contains("Ask to reschedule our meeting"))
            assertTrue(prepared.orEmpty().contains("do not send"))
        }
    }

    @Test fun writingProfileCanBeOmittedFromOneDraft() {
        var prepared: String? = null
        compose.setContent { MaterialTheme {
            WritingDraftSheetContent(WritingProfile(enabled = true, signOff = "Synthetic signature"), {}, {}, { prepared = it })
        } }
        compose.onNodeWithText("What do you want to say?").performTextInput("Say hello")
        compose.onNodeWithContentDescription("Use my writing profile").performScrollTo().performClick()
        compose.onNodeWithText("Prepare in chat").performScrollTo().performClick()
        compose.runOnIdle {
            assertNotNull(prepared)
            assertFalse(prepared.orEmpty().contains("Synthetic signature"))
            assertFalse(prepared.orEmpty().contains("My writing preferences"))
        }
    }

    @Test fun toneFeedbackRequiresExplicitRememberTap() {
        var remembered: WritingProfile? = null
        compose.setContent { MaterialTheme {
            WritingDraftSheetContent(WritingProfile(), { remembered = it }, {}, {})
        } }
        compose.onNodeWithText("Warm").performScrollTo().performClick()
        compose.runOnIdle { assertNull(remembered) }
        compose.onNodeWithText("Remember this tone for future drafts").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(DraftTone.WARM, remembered?.tone)
            assertEquals(true, remembered?.enabled)
        }
    }

    @Test fun calendarCardIsExplicitlyReadOnlyAndOpensTheChosenEvent() {
        val now = System.currentTimeMillis()
        val event = CalendarAgendaItem(42, "Synthetic planning session", now + 60_000, now + 3_600_000, false, "Desk")
        var opened: Long? = null
        compose.setContent {
            MaterialTheme {
                TodayHomeContent(
                    brief = TodayBrief.from(emptyList(), now), now = now,
                    calendarGranted = true, calendarEvents = listOf(event),
                    onOpenCalendarEvent = { opened = it.eventId }
                )
            }
        }
        compose.onNodeWithText("Synthetic planning session").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(42L, opened) }
    }

    @Test fun pausedComposerKeepsTheDraftButDisablesSendAndDictation() {
        compose.setContent { MaterialTheme { TestComposer(paused = true) } }
        compose.onNodeWithContentDescription("Message input").assertTextContains("My unsent thought")
        compose.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Dictate message").assertIsNotEnabled()
    }

    @Test fun shortcutSearchHidesPcToolsWhenFilteredForOfflineUse() {
        compose.setContent { MaterialTheme { TestComposer(tools = true) } }
        compose.onNodeWithText("Find a shortcut").performTextInput("weather")
        compose.onNodeWithText("Weather").assertExists()
        compose.onNodeWithText("Works without PC").performClick()
        compose.onNodeWithText("Weather").assertDoesNotExist()
        compose.onNodeWithText("No shortcuts found. Try camera, timer, or memory.").assertExists()
    }
}

@androidx.compose.runtime.Composable
private fun TestComposer(paused: Boolean = false, tools: Boolean = false) {
    Box(Modifier.fillMaxSize()) {
        ChatComposer(
            textInput = "My unsent thought", onTextChange = {}, canSendAttachment = false,
            isListening = false, isThinking = false, showToolsMenu = tools,
            onToggleToolsMenu = {}, onToolSelected = {}, onAttachFile = {}, onTakePhoto = {},
            onChoosePhoto = {}, onSendFileToPc = {}, onOpenPcExplorer = {}, onOpenActionHistory = {},
            onSend = {}, onQuickVoice = {}, onVoiceMode = {}, assistantPaused = paused
        )
    }
}
