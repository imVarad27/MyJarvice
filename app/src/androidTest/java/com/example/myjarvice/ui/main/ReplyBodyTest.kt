package com.example.myjarvice.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.myjarvice.theme.MyJarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReplyBodyTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun codeCopyIsAnExplicitExactContentCallback() {
        var copied: String? = null
        rule.setContent { MyJarvisTheme { ReplyBody("```kotlin\nprintln(1)\n```", onCopyCode = { copied = it }) } }
        assertNull(copied)
        rule.onNodeWithText("Copy code").performClick()
        assertEquals("println(1)", copied)
    }
    @Test fun streamingCodeDoesNotExposeAPrematureCopyAction() {
        rule.setContent { MyJarvisTheme { ReplyBody("```kotlin\nprintln(", allowCopy = false) } }
        rule.onNodeWithText("Copy code").assertIsNotEnabled()
        rule.onNodeWithText("println(").assertIsDisplayed()
    }
    @Test fun unusuallyLongCodeLineHasABoundedLayoutWithoutContentLoss() {
        val code = "x".repeat(5_000)
        rule.setContent { MyJarvisTheme { ReplyBody("```text\n$code\n```") } }
        rule.onNodeWithText(code).assertExists()
        rule.onNodeWithText("Copy code").assertIsDisplayed()
    }
}
