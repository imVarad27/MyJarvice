package com.example.myjarvice.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.example.myjarvice.theme.MyJarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic dialogs only. Run on an emulator, never instrument the user's personal install. */
class BackupPanelTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun protectionDefaultsOnAndRequiresMatchingPassphrases() {
        var received: CharArray? = null
        rule.setContent { MyJarvisTheme { BackupPassphraseDialog(true, true, false, null, {}, { received = it }) } }
        rule.onNodeWithText("Create backup").assertIsNotEnabled()
        rule.onNodeWithText("Passphrase").performTextInput("four clear words now")
        rule.onNodeWithText("Confirm passphrase").performTextInput("four wrong words now")
        rule.onNodeWithText("Passphrases do not match.").assertExists()
        rule.onNodeWithText("Create backup").assertIsNotEnabled()
        rule.onNodeWithText("Confirm passphrase").performTextClearance()
        rule.onNodeWithText("Confirm passphrase").performTextInput("four clear words now")
        rule.onNodeWithText("Create backup").performClick()
        assertArrayEquals("four clear words now".toCharArray(), received)
        rule.onNodeWithText("Passphrase").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test fun unprotectedExportIsExplicitAndShowsWarning() {
        var called = false
        var received: CharArray? = "sentinel".toCharArray()
        rule.setContent { MyJarvisTheme { BackupPassphraseDialog(true, true, false, null, {}, { called = true; received = it }) } }
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithText("This file will NOT be encrypted. Anyone with the file can read your personal data. Store it privately.").assertExists()
        rule.onNodeWithText("Create backup").performClick()
        assertTrue(called)
        assertNull(received)
    }

    @Test fun busyCardDisablesBothActionsAtLargeText() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MyJarvisTheme { BackupCard(true, null, true, {}, {}) }
            }
        }
        rule.onNodeWithText("Create backup").assertIsNotEnabled()
        rule.onNodeWithText("Restore backup").assertIsNotEnabled()
    }
}
