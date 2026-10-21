package com.example.myjarvice.ui.voice

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.myjarvice.theme.MyJarvisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VoiceModeScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun showsPartialTranscriptionAndMicrophoneStatus() {
        rule.setContent {
            VoiceModeScreen(isListening = true, isSpeaking = false, isThinking = false,
                micMuted = false, micLevel = 0f, onToggleMute = {}, onClose = {}, onInfo = {},
                onShare = {}, onChangeVoice = {}, liveTranscript = "Why is the sky",
                recognitionStatus = "Listening…")
        }
        rule.onNodeWithText("Why is the sky").assertExists()
        rule.onNodeWithText("Listening…").assertExists()
    }

    @Test fun showsRecognitionFailureInsteadOfFalseListeningState() {
        rule.setContent {
            VoiceModeScreen(isListening = false, isSpeaking = false, isThinking = false,
                micMuted = false, micLevel = 0f, onToggleMute = {}, onClose = {}, onInfo = {},
                onShare = {}, onChangeVoice = {}, recognitionStatus = "Microphone permission is needed")
        }
        rule.onNodeWithText("Microphone permission is needed").assertExists()
    }

    @Test fun retryIsExplicitAndDoesNotToggleMuteOrShare() {
        var retries = 0
        var mutes = 0
        var shares = 0
        rule.setContent { MyJarvisTheme {
            VoiceModeScreen(false, false, false, false, 0f, { mutes++ }, {}, {}, { shares++ }, {},
                onRetryListening = { retries++ })
        } }
        rule.onNodeWithContentDescription("Start listening").performClick()
        assertEquals(1, retries); assertEquals(0, mutes); assertEquals(0, shares)
        rule.onNodeWithText("Share transcript").assertDoesNotExist()
        rule.onNodeWithContentDescription("Voice options").performClick()
        rule.onNodeWithText("Share transcript").performClick()
        assertEquals(1, shares)
    }

    @Test fun mutedVoiceAndCloseStayReachableAtNarrowLargeText() {
        var unmuted = 0
        var closed = 0
        rule.setContent { MyJarvisTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Box(Modifier.width(320.dp)) {
                    VoiceModeScreen(false, false, false, true, 0f, { unmuted++ }, { closed++ }, {}, {}, {},
                        liveTranscript = "Synthetic transcript ".repeat(100))
                }
            }
        } }
        rule.onNodeWithContentDescription("Unmute microphone").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Close voice mode").assertIsDisplayed().performClick()
        assertEquals(1, unmuted); assertEquals(1, closed)
    }
}
