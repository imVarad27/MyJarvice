package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.ConnectionStatus
import com.example.myjarvice.theme.MyJarvisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun attachmentCanBeSentWithoutTyping() {
        var sent = 0
        compose.setContent { MyJarvisTheme { Composer(attachment = true, busy = false, onSend = { sent++ }) } }
        compose.onNodeWithContentDescription("Send message").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, sent) }
    }

    @Test fun processingDisablesSendAndNewAttachments() {
        compose.setContent { MyJarvisTheme { Composer(attachment = true, busy = true) } }
        compose.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Add attachment or tool").assertIsNotEnabled()
    }

    @Test fun toolbarHasAccessibleInboxAndHistory() {
        var inboxOpened = 0
        compose.setContent { MyJarvisTheme {
            ChatTopBar(ConnectionStatus.DISCONNECTED, {}, {}, {}, { inboxOpened++ })
        } }
        compose.onNodeWithContentDescription("Open saved inbox").performClick()
        compose.onNodeWithContentDescription("Open conversation history").assertHasClickAction()
        compose.runOnIdle { assertEquals(1, inboxOpened) }
    }

    @Test fun toolsExposePhotoAndDocumentChoices() {
        compose.setContent { MyJarvisTheme { Composer(menu = true) } }
        compose.onNodeWithText("Take a photo").assertIsDisplayed()
        compose.onNodeWithText("Choose a photo").assertIsDisplayed()
        compose.onNodeWithText("Attach text document").assertIsDisplayed()
    }

    @Test fun largeTextKeepsSendAndInputAvailable() {
        compose.setContent { MyJarvisTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Box(Modifier.width(320.dp)) { Composer(attachment = true) }
            }
        } }
        compose.onNodeWithContentDescription("Message input").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed().assertIsEnabled()
    }

    @androidx.compose.runtime.Composable
    private fun Composer(attachment: Boolean = false, busy: Boolean = false, menu: Boolean = false, onSend: () -> Unit = {}) {
        ChatComposer(textInput = "", onTextChange = {}, canSendAttachment = attachment,
            isListening = false, isThinking = busy, showToolsMenu = menu, onToggleToolsMenu = {},
            onToolSelected = {}, onAttachFile = {}, onTakePhoto = {}, onChoosePhoto = {},
            onSendFileToPc = {}, onOpenPcExplorer = {}, onSend = onSend, onQuickVoice = {}, onVoiceMode = {})
    }
}
