package com.example.myjarvice.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.*
import com.example.myjarvice.theme.MyJarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic metadata only; no provider, phone data, clipboard, model or calendar intents. */
class TimeRehearsalCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val sample = TimeRehearsalResult(true, 60, 10, 90, listOf(
        RehearsalOption(1_000_000, 4_600_000, 400_000, 5_200_000, 6_400_000, false)))

    @Test fun collapsedCardExplainsPrivacyAndDetailsRequireATap() {
        rule.setContent { MyJarvisTheme {
            var expanded by remember { mutableStateOf(false) }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimeRehearsalCard(sample, 10, expanded, { expanded = it }, {})
            }
        } }
        rule.onNodeWithText("Private calculation · no AI call · nothing booked").assertIsDisplayed()
        rule.onNodeWithText("Preparation and recovery").assertDoesNotExist()
        rule.onNodeWithText("Rehearse a commitment").performClick()
        rule.onNodeWithText("Preparation and recovery").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("1 sampled slot · 0 also fit the overrun").performScrollTo().assertIsDisplayed()
    }

    @Test fun incompleteDataDoesNotShowZeroAsAValidAvailabilityResult() {
        rule.setContent { MyJarvisTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimeRehearsalCard(sample.copy(available = false, options = emptyList()), 10, true, {}, {})
            }
        } }
        rule.onNodeWithText("Rehearsal unavailable: calendar data is incomplete. Refresh before trying again.")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("0 sampled slots · 0 also fit the overrun").assertDoesNotExist()
    }

    @Test fun recalculationMasksPriorResultsAndDisablesBufferChanges() {
        rule.setContent { MyJarvisTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimeRehearsalCard(sample, 10, true, {}, {}, calculating = true)
            }
        } }
        rule.onNodeWithText("Recalculating rehearsal…").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("30 min each side").assertIsNotEnabled()
        rule.onNodeWithText("1 sampled slot · 0 also fit the overrun").assertDoesNotExist()
    }

    @Test fun bufferControlsAndEmptyFilteredStateAreReachableAtNarrowLargeText() {
        var chosen = -1
        rule.setContent { MyJarvisTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    TimeRehearsalCard(sample, 10, true, {}, { chosen = it })
                }
            }
        } }
        rule.onNodeWithText("30 min each side").performScrollTo().performClick()
        assertEquals(30, chosen)
        rule.onNodeWithText("Only overrun-safe slots").performScrollTo().performClick()
        rule.onNodeWithText("None of these sampled slots fits the 50% overrun. The original commitment may fit, but has less margin.")
            .performScrollTo().assertIsDisplayed()
    }
}
