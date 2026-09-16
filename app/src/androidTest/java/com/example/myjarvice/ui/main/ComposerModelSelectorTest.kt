package com.example.myjarvice.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.myjarvice.data.SmartMode
import com.example.myjarvice.theme.MyJarvisTheme
import org.junit.Rule
import org.junit.Test

class ComposerModelSelectorTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun exposesCurrentRouteAndSwitchesFromComposer() {
        val mode = mutableStateOf(SmartMode.STRONG_HOST)
        rule.setContent {
            MyJarvisTheme {
                ComposerModelSelector(mode.value, { mode.value = SmartMode.FAST_ON_DEVICE })
            }
        }
        rule.onNodeWithContentDescription("Choose AI model: PC").performClick()
        rule.onNodeWithContentDescription("Choose AI model: Phone").assertExists()
    }
}
