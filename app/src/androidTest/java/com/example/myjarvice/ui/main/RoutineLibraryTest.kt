package com.example.myjarvice.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.myjarvice.data.LocalRoutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Uses synthetic routines only; it never reads phone tasks or creates them. */
class RoutineLibraryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun runControlOnlyRequestsTheExplicitConfirmationStep() {
        val routine = LocalRoutine("synthetic", "Meeting reset", listOf("Open notes", "Choose next step"), 1, 1)
        var requested: LocalRoutine? = null
        compose.setContent { MaterialTheme { RoutineLibraryContent(listOf(routine), {}, {}, {}, { requested = it }) } }

        compose.onNodeWithText("Run").performClick()
        compose.runOnIdle { assertEquals(routine, requested) }
    }

    @Test fun routineEditorDoesNotSaveInvalidBlankSteps() {
        var saved: String? = null
        compose.setContent { MaterialTheme { RoutineEditorDialog(null, {}, { title, _ -> saved = title }) } }

        compose.onNodeWithText("Save routine").assertIsNotEnabled()
        compose.runOnIdle { assertNull(saved) }
    }
}
