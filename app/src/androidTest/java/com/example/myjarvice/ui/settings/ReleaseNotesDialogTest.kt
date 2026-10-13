package com.example.myjarvice.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.example.myjarvice.data.AppVersionInfo
import com.example.myjarvice.data.JarvisReleases
import com.example.myjarvice.theme.MyJarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Static UI only; synthetic metadata and callbacks, no clipboard access or account/device reads. */
class ReleaseNotesDialogTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun showsBuildAndHighlightsAndCopiesOnlyAfterExplicitTap() {
        var copies = 0
        var closed = 0
        rule.setContent { MyJarvisTheme {
            ReleaseNotesContent(AppVersionInfo(JarvisReleases.latest.version, 12, true), JarvisReleases.latest,
                { closed++ }, { copies++ })
        } }
        rule.onNodeWithText("Jarvis ${JarvisReleases.latest.version}").assertIsDisplayed()
        rule.onNodeWithText("Build 12 · Development build").assertIsDisplayed()
        assertEquals(0, copies)
        rule.onNodeWithText("Copy app version").performClick()
        assertEquals(1, copies)
        rule.onNodeWithText("Review before sharing").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Close release notes").performClick()
        assertEquals(1, closed)
    }

    @Test fun staleNotesAreHiddenRatherThanRelabelledAsANewerInstalledVersion() {
        rule.setContent { MyJarvisTheme {
            ReleaseNotesContent(AppVersionInfo("9.8.7", 1234, false), JarvisReleases.latest, {}, {})
        } }
        rule.onNodeWithText("Jarvis 9.8.7").assertIsDisplayed()
        rule.onNodeWithText("Build 1234 · Release build").assertIsDisplayed()
        rule.onNodeWithText("In this update").assertDoesNotExist()
        rule.onNodeWithText("Review before sharing").assertDoesNotExist()
        rule.onNodeWithText("Release notes aren't available for this build. The installed version above is still accurate.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun largeTextKeepsCopyAndFinalHighlightReachable() {
        rule.setContent { MyJarvisTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                ReleaseNotesContent(AppVersionInfo(JarvisReleases.latest.version, 12, true), JarvisReleases.latest, {}, {})
            }
        } }
        rule.onNodeWithText("Copy app version").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Protect your local backup").performScrollTo().assertIsDisplayed()
    }
}
