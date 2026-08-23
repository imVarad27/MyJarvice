package com.example.myjarvice.data

import android.content.Context
import com.example.myjarvice.theme.ThemeMode

/**
 * Lightweight persistence for app-level preferences, backed by SharedPreferences.
 *
 * Kept intentionally dependency-free (no DataStore) for the current app size —
 * this is the "pragmatic architecture" choice. If preferences grow or need
 * reactive flows across processes, migrate to DataStore.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DYNAMIC, value).apply()
        }

    /** Picovoice access key for the "Hi Jarvis" wake word (from console.picovoice.ai). */
    var picovoiceKey: String
        get() = prefs.getString(KEY_PICOVOICE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_PICOVOICE, value.trim()).apply()
        }

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_WAKE, value).apply()
        }

    /** Host PC address for the websocket link, so the app reconnects without retyping it. */
    var serverIp: String
        get() = prefs.getString(KEY_SERVER_IP, DEFAULT_SERVER_IP) ?: DEFAULT_SERVER_IP
        set(value) {
            prefs.edit().putString(KEY_SERVER_IP, value.trim()).apply()
        }

    /** Pairing token configured on the host; never included in chat payloads. */
    var serverToken: String
        get() = prefs.getString(KEY_SERVER_TOKEN, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SERVER_TOKEN, value.trim()).apply() }

    /**
     * Name of the TTS voice chosen in voice mode's "Change Voice" sheet.
     * Empty means "whatever the engine defaults to".
     */
    var ttsVoice: String
        get() = prefs.getString(KEY_TTS_VOICE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_TTS_VOICE, value).apply()
        }

    companion object {
        /** Matches the client default; overridden as soon as the user sets an address. */
        const val DEFAULT_SERVER_IP = ""

        private const val PREFS_NAME = "jarvic_settings"
        private const val KEY_SERVER_IP = "server_ip"
        private const val KEY_SERVER_TOKEN = "server_token"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_PICOVOICE = "picovoice_key"
        private const val KEY_WAKE = "wake_word_enabled"
        private const val KEY_TTS_VOICE = "tts_voice"
    }
}
