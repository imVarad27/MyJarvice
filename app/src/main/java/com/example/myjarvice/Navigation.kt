package com.example.myjarvice

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.myjarvice.theme.ThemeMode
import com.example.myjarvice.ui.main.MainScreen
import com.example.myjarvice.ui.settings.SettingsScreen
import com.example.myjarvice.ui.splash.SplashScreen
import com.example.myjarvice.ui.welcome.WelcomeScreen

@Composable
fun MainNavigation(
    startOnChat: Boolean,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    wakeEnabled: Boolean,
    onWakeEnabled: (Boolean) -> Unit
) {
    // A wake-word launch jumps straight to the chat; a normal launch shows the splash.
    val backStack = rememberNavBackStack(if (startOnChat) Main else Splash)

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Splash> {
                SplashScreen(onFinish = {
                    backStack.clear()
                    backStack.add(Welcome)
                })
            }
            entry<Welcome> {
                WelcomeScreen(
                    onStartChat = { backStack.add(Main) },
                    onVoiceMode = { backStack.add(Main) },
                    onSettings = { backStack.add(Settings) }
                )
            }
            entry<Main> {
                MainScreen(modifier = Modifier.safeDrawingPadding())
            }
            entry<Settings> {
                SettingsScreen(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    onThemeMode = onThemeMode,
                    onDynamicColor = onDynamicColor,
                    wakeEnabled = wakeEnabled,
                    onWakeEnabled = onWakeEnabled,
                    onBack = { if (backStack.size > 1) backStack.removeLastOrNull() }
                )
            }
        }
    )
}
