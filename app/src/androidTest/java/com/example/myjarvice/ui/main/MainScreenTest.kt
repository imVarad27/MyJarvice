package com.example.myjarvice.ui.main

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.myjarvice.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent { Text("JARVICE test surface") }
  }

  @Test
  fun firstItem_exists() {
    composeTestRule.onNodeWithText("JARVICE test surface").assertExists()
  }
}
