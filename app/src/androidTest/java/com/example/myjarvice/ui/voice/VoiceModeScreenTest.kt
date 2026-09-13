package com.example.myjarvice.ui.voice

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
}
