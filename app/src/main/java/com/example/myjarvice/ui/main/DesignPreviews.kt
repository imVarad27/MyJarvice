package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.myjarvice.data.ConnectionStatus
import com.example.myjarvice.theme.MyJarvisTheme
import com.example.myjarvice.theme.ThemeMode
import com.example.myjarvice.ui.voice.VoiceModeScreen
import com.example.myjarvice.ui.welcome.WelcomeScreen

@Preview(name = "Chat · light", widthDp = 393, heightDp = 852)
@Preview(name = "Chat · large text", widthDp = 320, heightDp = 740, fontScale = 1.5f)
@Composable
private fun LightChatPreview() = BlankChatPreview(ThemeMode.LIGHT)

@Preview(name = "Chat · dark", widthDp = 393, heightDp = 852)
@Composable
private fun DarkChatPreview() = BlankChatPreview(ThemeMode.DARK)

@Composable
private fun BlankChatPreview(theme: ThemeMode) {
    MyJarvisTheme(themeMode = theme) {
        Surface {
            Column(Modifier.fillMaxSize()) {
                ChatTopBar(ConnectionStatus.DISCONNECTED, {}, {}, {}, {})
                Box(Modifier.weight(1f)) {
                    TodayHomeContent(
                        brief = com.example.myjarvice.data.TodayBrief.from(emptyList(), System.currentTimeMillis()),
                        now = System.currentTimeMillis()
                    )
                }
                ChatComposer("", {}, false, false, false, false, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
    }
}

@Preview(name = "Welcome", widthDp = 393, heightDp = 852)
@Composable
private fun WelcomePreview() {
    MyJarvisTheme(themeMode = ThemeMode.LIGHT) { WelcomeScreen({}, {}, {}) }
}

@Preview(name = "Voice · dark", widthDp = 393, heightDp = 852)
@Composable
private fun VoicePreview() {
    MyJarvisTheme(themeMode = ThemeMode.DARK) {
        VoiceModeScreen(false, false, false, true, 0f, {}, {}, {}, {}, {})
    }
}
