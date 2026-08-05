package com.example.myjarvice

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.theme.MyJarviceTheme
import com.example.myjarvice.wake.WakeEvents
import com.example.myjarvice.wake.WakeWordService

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestCorePermissions()

        val store = SettingsStore(applicationContext)
        // Keep the wake-word service running if the user enabled it.
        if (store.wakeWordEnabled) {
            WakeWordService.start(applicationContext)
        }

        val launchedByWake = intent?.getBooleanExtra(EXTRA_START_VOICE, false) == true
        if (launchedByWake) WakeEvents.voiceTrigger.value = true

        enableEdgeToEdge()
        setContent {
            val settingsStore = remember { SettingsStore(applicationContext) }

            var themeMode by remember { mutableStateOf(settingsStore.themeMode) }
            var dynamicColor by remember { mutableStateOf(settingsStore.dynamicColor) }
            var wakeEnabled by remember { mutableStateOf(settingsStore.wakeWordEnabled) }

            MyJarviceTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        startOnChat = launchedByWake,
                        themeMode = themeMode,
                        dynamicColor = dynamicColor,
                        onThemeMode = { themeMode = it; settingsStore.themeMode = it },
                        onDynamicColor = { dynamicColor = it; settingsStore.dynamicColor = it },
                        wakeEnabled = wakeEnabled,
                        onWakeEnabled = { enabled ->
                            wakeEnabled = enabled
                            settingsStore.wakeWordEnabled = enabled
                            if (enabled) {
                                ensureOverlayAndBattery()
                                WakeWordService.start(applicationContext)
                            } else {
                                WakeWordService.stop(applicationContext)
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

    /** Ask for "display over other apps" (for the pop-up) and battery-opt exemption. */
    private fun ensureOverlayAndBattery() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) { /* Some OEMs restrict this intent; ignore. */ }
    }

    companion object {
        const val EXTRA_START_VOICE = "start_voice"
    }
}
