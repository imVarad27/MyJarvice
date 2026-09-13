package com.example.myjarvice

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import com.example.myjarvice.theme.ThemeMode
import androidx.compose.ui.Modifier
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.theme.MyJarviceTheme
import com.example.myjarvice.wake.WakeEvents
import com.example.myjarvice.wake.WakeWordService

class MainActivity : ComponentActivity() {
    private var inboxRequest by mutableStateOf(0L)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launchedByWake = intent?.getBooleanExtra(EXTRA_START_VOICE, false) == true
        if (launchedByWake) requestCorePermissions()
        if (intent?.getBooleanExtra(EXTRA_OPEN_INBOX, false) == true) inboxRequest = System.nanoTime()
        if (launchedByWake) WakeEvents.voiceTrigger.value = true

        enableEdgeToEdge()
        setContent {
            val settingsStore = remember { SettingsStore(applicationContext) }

            var themeMode by remember { mutableStateOf(settingsStore.themeMode) }
            var dynamicColor by remember { mutableStateOf(settingsStore.dynamicColor) }
            var wakeEnabled by remember { mutableStateOf(settingsStore.wakeWordEnabled) }
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) wakeEnabled = settingsStore.wakeWordEnabled
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            val wakePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                wakeEnabled = granted
                settingsStore.wakeWordEnabled = granted
                if (granted) {
                    WakeWordService.start(applicationContext)
                    requestNotificationPermission()
                } else Toast.makeText(this, "Microphone access is needed for hands-free voice.", Toast.LENGTH_LONG).show()
            }
            val darkBars = themeMode == ThemeMode.DARK || themeMode == ThemeMode.AMOLED ||
                (themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkBars
                    isAppearanceLightNavigationBars = !darkBars
                }
            }

            MyJarviceTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        startOnChat = true,
                        inboxRequest = inboxRequest,
                        themeMode = themeMode,
                        dynamicColor = dynamicColor,
                        onThemeMode = { themeMode = it; settingsStore.themeMode = it },
                        onDynamicColor = { dynamicColor = it; settingsStore.dynamicColor = it },
                        wakeEnabled = wakeEnabled,
                        onWakeEnabled = { enabled ->
                            if (enabled && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                wakePermission.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                wakeEnabled = enabled
                                settingsStore.wakeWordEnabled = enabled
                                if (enabled) {
                                    WakeWordService.start(applicationContext)
                                    requestNotificationPermission()
                                } else WakeWordService.stop(applicationContext)
                            }
                        }
                    )
                }
            }
        }
    }

    // singleTask: a wake-word launch while already running arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_INBOX, false)) inboxRequest = System.nanoTime()
        if (intent.getBooleanExtra(EXTRA_START_VOICE, false)) {
            WakeEvents.voiceTrigger.value = true
        }
    }

    private fun requestCorePermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        WakeEvents.appVisible.value = true
        if (SettingsStore(this).wakeWordEnabled) WakeWordService.start(this)
    }

    override fun onPause() {
        WakeEvents.appVisible.value = false
        super.onPause()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    companion object {
        const val EXTRA_START_VOICE = "start_voice"
        const val EXTRA_OPEN_INBOX = "open_inbox"
    }
}
