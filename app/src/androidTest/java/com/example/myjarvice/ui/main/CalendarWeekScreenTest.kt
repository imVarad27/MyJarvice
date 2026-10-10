package com.example.myjarvice.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
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
import java.util.Calendar

/** Synthetic screens only. Never reads the phone calendar or launches real system intents. */
class CalendarWeekScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 10, 8, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    @Test fun accessIsExplicitAndPermissionSettingsRemainReachable() {
        var requests = 0
        var settings = 0
        rule.setContent { MyJarvisTheme { CalendarWeekScreen(emptyList(), false, false, false, false, null, now, 60,
            {}, {}, { requests++ }, { settings++ }, {}, {}) } }
        assertEquals(0, requests)
        rule.onNodeWithText("Allow read-only access").performScrollTo().performClick()
        rule.onNodeWithText("Open permission settings").performScrollTo().performClick()
        assertEquals(1, requests)
        assertEquals(1, settings)
        rule.onNodeWithText("Find an opening").assertDoesNotExist()
    }

    @Test fun incompleteScheduleShowsWarningAndNoSuggestedTimes() {
        val days = CalendarWeekPlanner.plan(CalendarSnapshot(emptyList(), true), now)
        rule.setContent { MyJarvisTheme { CalendarWeekScreen(days, true, false, true, true, null, now, 60,
            {}, {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("Calendar data is incomplete or exceeds the safety limit. Openings are hidden to avoid suggesting occupied time.")
            .performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Unavailable with incomplete calendar data.").onFirst().performScrollTo().assertIsDisplayed()
    }

    @Test fun durationAndRefreshAreUserActionsAtNarrowLargeText() {
        var duration = 0
        var refreshes = 0
        rule.setContent { MyJarvisTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Box(Modifier.width(320.dp)) { CalendarWeekScreen(emptyList(), true, false, true, false, null, now, 60,
                    {}, { refreshes++ }, {}, {}, { duration = it }, {}) }
            }
        } }
        rule.onNodeWithText("90 min").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Refresh week planner").performClick()
        assertEquals(90, duration)
        assertEquals(1, refreshes)
    }

    @Test fun busyScreenHidesStaleDataAndDisablesRefresh() {
        rule.setContent { MyJarvisTheme { CalendarWeekScreen(emptyList(), true, true, true, false, null, now, 60,
            {}, {}, {}, {}, {}, {}) } }
        rule.onNodeWithContentDescription("Refresh week planner").assertIsNotEnabled()
        rule.onNodeWithText("Find an opening").assertDoesNotExist()
    }

    @Test fun eventTapOpensOnlyTheChosenInstanceAndDoesNotReserveTime() {
        val event = CalendarAgendaItem(42, "Synthetic meeting", now + 3_600_000, now + 7_200_000, false, "Synthetic room")
        val days = CalendarWeekPlanner.plan(CalendarSnapshot(listOf(event)), now)
        var opened: CalendarAgendaItem? = null
        rule.setContent { MyJarvisTheme { CalendarWeekScreen(days, true, false, true, false, null, now, 60,
            {}, {}, {}, {}, {}, { opened = it }) } }
        assertNull(opened)
        rule.onNodeWithText("Synthetic meeting").performScrollTo().performClick()
        assertEquals(event, opened)
    }
}
