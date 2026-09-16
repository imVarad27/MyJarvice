package com.example.myjarvice

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.theme.MyJarvisTheme
import com.example.myjarvice.ui.main.MainScreenViewModel
import com.example.myjarvice.ui.popup.AssistantPopupScreen
import com.example.myjarvice.wake.WakeEvents

/** Private, translucent assistant surface. Never reveals chat over a locked phone. */
class AssistantPopupActivity : ComponentActivity() {
    companion object {
        const val EXTRA_VERIFIED_WAKE = "verified_wake"
    }
    private lateinit var model: MainScreenViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) { finish(); return }
        WakeEvents.popupVisible.value = true
        WakeEvents.setMicrophoneBusy("popup", true)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[MainScreenViewModel::class.java]
        model.useAsPopup()
        val settings = SettingsStore(this)
        val verifiedWake = savedInstanceState == null && intent.getBooleanExtra(EXTRA_VERIFIED_WAKE, false)
        setContent {
            MyJarvisTheme(settings.themeMode, settings.dynamicColor, settings.assistantStyle) {
                AssistantPopupScreen(
                    model = model,
                    verifiedWake = verifiedWake,
                    style = settings.assistantStyle,
                    onDismiss = { finish() },
                    onExpand = {
                        model.pauseVoiceForTyping()
                        val session = model.saveForHandoff()
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            session?.let { putExtra(MainActivity.EXTRA_OPEN_SESSION, it) }
                        })
                        finish()
                    }
                )
            }
        }
    }

    override fun onStop() {
        if (::model.isInitialized) model.exitVoiceMode()
        super.onStop()
        // Do not leave an invisible assistant window holding the microphone.
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        WakeEvents.popupVisible.value = false
        WakeEvents.setMicrophoneBusy("popup", false)
        super.onDestroy()
    }
}
