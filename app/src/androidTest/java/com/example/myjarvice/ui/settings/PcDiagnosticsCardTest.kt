package com.example.myjarvice.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.example.myjarvice.data.PcCheck
import com.example.myjarvice.data.PcDiagnosticReport
import com.example.myjarvice.theme.MyJarvisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic UI only; never contacts a host or reads saved credentials. */
class PcDiagnosticsCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun checkIsExplicitAndColdReadinessDoesNotClaimGeneration() {
        var checks = 0
        rule.setContent { MyJarvisTheme { PcDiagnosticsCard(false,
            PcDiagnosticReport(PcCheck.INSTALLED, "synthetic", false, 100), { checks++ }, {}) } }
        assertEquals(0, checks)
        rule.onNodeWithText("Installed but not loaded · the first reply may take longer.").assertExists()
        rule.onNodeWithText("Check again").performClick()
        assertEquals(1, checks)
    }
    @Test fun runningCheckHasACancelControlAtLargeText() {
        var cancels = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MyJarvisTheme { PcDiagnosticsCard(true, null, {}, { cancels++ }) }
            }
        }
        rule.onNodeWithText("Cancel check").assertIsDisplayed().performClick()
        assertEquals(1, cancels)
        rule.onNodeWithText("Check connection").assertDoesNotExist()
    }
}
