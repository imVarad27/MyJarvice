package com.example.myjarvice.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.example.myjarvice.data.KnowledgeEntry
import com.example.myjarvice.theme.MyJarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic content and callbacks only; no personal store or screenshots. */
class KnowledgeLibraryScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val fact = KnowledgeEntry("m", "Saved memory", "Synthetic short answers preference", true)
    private val document = KnowledgeEntry("d", "Synthetic physics notes", "Exam Friday", false, false)
    private val rows = mutableStateOf(listOf(fact, document))
    private var removes = 0
    private fun show(fontScale: Float = 1f) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MyJarvisTheme {
                    KnowledgeLibraryScreen(rows.value, false, true, null, null,
                        onDismiss = {}, onRefresh = {}, onImport = {},
                        onSave = { entry, text, done ->
                            rows.value = rows.value.map { if (it.id == entry?.id) it.copy(text = text) else it }
                            done()
                        }, onToggle = { entry, enabled ->
                            rows.value = rows.value.map { if (it.id == entry.id) it.copy(enabled = enabled) else it }
                        }, onDelete = { entry, done ->
                            removes++
                            rows.value = rows.value.filterNot { it.id == entry.id }
                            done()
                        })
                }
            }
        }
    }

    @Test fun searchAndExcludedFilterKeepStoredItemsInspectable() {
        show()
        rule.onNodeWithText("Excluded").performClick()
        rule.onNodeWithText(document.name).assertExists()
        rule.onNodeWithText(fact.text).assertDoesNotExist()
        rule.onNodeWithText("All").performClick()
        rule.onNodeWithText("Search library").performTextInput("physics")
        rule.onNodeWithText(document.name).assertExists()
        rule.onNodeWithText(fact.text).assertDoesNotExist()
    }

    @Test fun itemCanBeExcludedAndMemoryEditMustBeSaved() {
        show()
        rule.onNodeWithText(fact.text).performScrollTo().performClick()
        rule.onNodeWithContentDescription("Use this item in phone-model tools").performClick()
        rule.onNodeWithContentDescription("Use this item in phone-model tools").assertIsOff()
        rule.onNodeWithContentDescription("Edit memory").performClick()
        rule.onNodeWithText("Memory text").performTextReplacement("Updated synthetic preference")
        assertEquals(fact.text, rows.value.first().text)
        rule.onNodeWithText("Save memory").performClick()
        assertEquals("Updated synthetic preference", rows.value.first().text)
        assertFalse(rows.value.first().enabled)
    }

    @Test fun removeRequiresConfirmationAndCancelDoesNothing() {
        show()
        rule.onNodeWithText(fact.text).performScrollTo().performClick()
        rule.onNodeWithContentDescription("Remove saved item").performClick()
        assertEquals(0, removes)
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(0, removes)
        rule.onNodeWithContentDescription("Remove saved item").performClick()
        rule.onNodeWithText("Remove").performClick()
        assertEquals(1, removes)
        assertEquals(listOf(document), rows.value)
    }

    @Test fun largeTextKeepsNavigationAndItemDetailsReachable() {
        show(fontScale = 2f)
        rule.onNodeWithContentDescription("Close library").assertIsDisplayed()
        rule.onNodeWithText(document.name).performScrollTo().performClick()
        rule.onNodeWithContentDescription("Close item").assertIsDisplayed()
        rule.onNodeWithContentDescription("Edit memory").assertDoesNotExist()
        rule.onNodeWithText(document.text).performScrollTo().assertIsDisplayed()
    }
}
