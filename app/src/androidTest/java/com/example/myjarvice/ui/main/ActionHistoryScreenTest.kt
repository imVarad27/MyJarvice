package com.example.myjarvice.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.example.myjarvice.data.ActionAuditEvent
import com.example.myjarvice.data.ActionTimeline
import com.example.myjarvice.theme.MyJarvisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic UI fixtures only. Run on a dedicated emulator, not a personal phone. */
class ActionHistoryScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val phone = ActionTimeline.localEvent(1, 1_728_000_000_000, "tool.phone_status", "completed")
    private val pc = ActionAuditEvent(1, "2024-10-04T00:00:00Z", "tool.pc_status", "failed", "Synthetic PC failure", "pc")

    private fun show(events: List<ActionAuditEvent> = listOf(phone, pc), fontScale: Float = 1f,
                     onClear: () -> Unit = {}) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MyJarvisTheme {
                    ActionHistoryDialog(events, false, null, onDismiss = {}, onRefresh = {}, onClearPhone = onClear)
                }
            }
        }
    }

    @Test fun searchAndSourceFiltersKeepPhoneAndPcSeparate() {
        show()
        rule.onNodeWithText("Phone status").assertExists()
        rule.onNodeWithText("PC status").assertExists()
        rule.onNodeWithText("Phone", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Phone status").assertExists()
        rule.onNodeWithText("PC status").assertDoesNotExist()
        rule.onNodeWithText("All", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Search activity").performTextInput("Synthetic PC")
        rule.onNodeWithText("Phone status").assertDoesNotExist()
        rule.onNodeWithText("PC status").assertExists()
        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.onNodeWithText("Phone status").assertExists()
    }

    @Test fun attentionFilterShowsFailedActions() {
        show()
        rule.onNodeWithText("All outcomes").performClick()
        rule.onNodeWithText("Needs attention").performClick()
        rule.onNodeWithText("PC status").assertExists()
        rule.onNodeWithText("Phone status").assertDoesNotExist()
    }

    @Test fun clearingPhoneLogRequiresExplicitConfirmation() {
        var clears = 0
        show(onClear = { clears++ })
        rule.onNodeWithContentDescription("Clear phone activity log").performClick()
        assertEquals(0, clears)
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(0, clears)
        rule.onNodeWithContentDescription("Clear phone activity log").performClick()
        rule.onNodeWithText("Clear phone log").performClick()
        assertEquals(1, clears)
    }

    @Test fun largeTextKeepsNavigationAndEmptyStateAvailable() {
        show(events = emptyList(), fontScale = 2f)
        rule.onNodeWithContentDescription("Close activity").assertIsDisplayed()
        rule.onNodeWithContentDescription("Refresh PC activity").assertIsDisplayed()
        rule.onNodeWithText("Your activity starts here").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Clear phone activity log").assertIsNotEnabled()
    }
}
