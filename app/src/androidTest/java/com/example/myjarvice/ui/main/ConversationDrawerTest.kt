package com.example.myjarvice.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.ChatSession
import com.example.myjarvice.data.JarvisMessage
import com.example.myjarvice.theme.MyJarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic sessions only. No ViewModel, history store, provider, microphone or real deletion. */
class ConversationDrawerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val session = ChatSession("synthetic", "Sketch ideas", 1, 2, listOf(JarvisMessage("USER", "Unique query phrase")))

    @Test fun searchMatchesMessageTextWithoutDisplayingPrivateSnippets() {
        rule.setContent { MyJarvisTheme { HistoryDrawerContent(listOf(session), {}, {}, {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("Search conversations").performTextInput("Unique query phrase")
        rule.onNodeWithText("Sketch ideas").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Unique query phrase", substring = false).assertExists() // input only
        rule.onAllNodesWithText("Unique query phrase", substring = false).assertCountEquals(1)
    }
    @Test fun clearAllIsBehindAnExplicitMenuAndOnlyInvokesAnApprovalCallback() {
        var requests = 0
        rule.setContent { MyJarvisTheme { HistoryDrawerContent(listOf(session), {}, {}, {}, { requests++ }, {}, {}, {}) } }
        assertEquals(0, requests)
        rule.onNodeWithText("Clear conversations").assertDoesNotExist()
        rule.onNodeWithContentDescription("Conversation options").performClick()
        rule.onNodeWithText("Clear conversations").performClick()
        assertEquals(1, requests)
        rule.onNodeWithText("Sketch ideas").assertExists()
    }
    @Test fun settingsAndScrollableDestinationsRemainReachableAtShortLargeText() {
        var settings = 0
        var today = 0
        rule.setContent { MyJarvisTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Box(Modifier.width(320.dp).height(400.dp)) {
                    HistoryDrawerContent(listOf(session), {}, {}, {}, {}, { settings++ }, { today++ }, {})
                }
            }
        } }
        rule.onNodeWithText("Settings").assertIsDisplayed().performClick()
        rule.onNodeWithText("Today").performScrollTo().performClick()
        assertEquals(1, settings); assertEquals(1, today)
    }
    @Test fun generatingKeepsConversationSwitchAndDeletionDisabled() {
        rule.setContent { MyJarvisTheme {
            HistoryDrawerContent(listOf(session), {}, {}, {}, {}, {}, {}, {}, canChangeConversation = false)
        } }
        rule.onNodeWithText("New chat").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Delete conversation Sketch ideas").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithContentDescription("Conversation options").performClick()
        rule.onNodeWithText("Clear conversations").assertIsNotEnabled()
    }
}
