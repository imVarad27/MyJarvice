package com.example.myjarvice

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.myjarvice.theme.ThemeMode
import com.example.myjarvice.ui.main.MainScreen
import com.example.myjarvice.ui.settings.SettingsScreen
import com.example.myjarvice.ui.splash.SplashScreen

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
    // Splash screen briefly initializes then transitions directly to Main Chat!
    val backStack = rememberNavBackStack(if (startOnChat) Main else Splash)

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Splash> {
                SplashScreen(onFinish = {
                    backStack.clear()
                    backStack.add(Main)
                })
            }
            entry<Main> {
                MainScreen(
                    onOpenSettings = { backStack.add(Settings) },
                    modifier = Modifier.systemBarsPadding()
                )
            }
            entry<Settings> {
                SettingsScreen(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    onThemeMode = onThemeMode,
                    onDynamicColor = onDynamicColor,
                    wakeEnabled = wakeEnabled,
                    onWakeEnabled = onWakeEnabled,
                    onOpenVoiceMatch = { backStack.add(VoiceMatchEnrollment) },
                    onBack = { if (backStack.size > 1) backStack.removeLastOrNull() }
                )
            }
            entry<VoiceMatchEnrollment> {
                com.example.myjarvice.ui.settings.VoiceMatchEnrollmentScreen(
                    onFinished = {
                        if (backStack.size > 1) backStack.removeLastOrNull()
                    },
                    onBack = {
                        if (backStack.size > 1) backStack.removeLastOrNull()
                    }
                )
            }
        }
    )
}
