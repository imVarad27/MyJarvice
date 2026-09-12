package com.example.myjarvice

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.widget.Toast
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
    inboxRequest: Long = 0L,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    wakeEnabled: Boolean,
    onWakeEnabled: (Boolean) -> Unit
) {
    // Splash screen briefly initializes then transitions directly to Main Chat!
    val backStack = rememberNavBackStack(if (startOnChat) Main else Splash)
    val context = LocalContext.current
    val voiceSetupPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) backStack.add(VoiceMatchEnrollment)
        else Toast.makeText(context, "Allow microphone access to set up your voice.", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(inboxRequest) {
        if (inboxRequest > 0) { backStack.clear(); backStack.add(Main) }
    }

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
                    inboxRequest = inboxRequest,
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
                    onOpenVoiceMatch = { voiceSetupPermission.launch(Manifest.permission.RECORD_AUDIO) },
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
