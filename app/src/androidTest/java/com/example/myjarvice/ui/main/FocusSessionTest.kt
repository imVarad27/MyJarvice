package com.example.myjarvice.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.myjarvice.data.LocalTask
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic UI only; this test never reads or changes a person's task list. */
class FocusSessionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun focusSetupRequiresAnExplicitStartForTheChosenTask() {
        val task = LocalTask("synthetic-focus", "Write a synthetic outline", createdAt = 1)
        var selected: Pair<LocalTask, Int>? = null
        compose.setContent { MaterialTheme { FocusSetupContent(listOf(task)) { picked, minutes -> selected = picked to minutes } } }

        compose.onNodeWithText("Start focus").performClick()
        compose.runOnIdle { assertEquals(task to 25, selected) }
    }

    @Test fun focusDialogDoesNotOfferCompletionBeforeTheTimerEnds() {
        val task = LocalTask("synthetic-active", "Keep task open", createdAt = 1)
        compose.setContent { MaterialTheme {
            FocusSessionDialog(task, 25, System.currentTimeMillis(), onDismiss = {}, onMarkComplete = {})
        } }
        compose.onNodeWithText("Mark complete").assertDoesNotExist()
        compose.onNodeWithText("Pause").assertIsDisplayed()
    }
}
