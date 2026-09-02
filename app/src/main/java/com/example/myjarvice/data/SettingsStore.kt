package com.example.myjarvice.data

import android.content.Context
import com.example.myjarvice.theme.ThemeMode

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

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE, true)
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
        get() = prefs.getString(KEY_SERVER_TOKEN, DEFAULT_SERVER_TOKEN) ?: DEFAULT_SERVER_TOKEN
        set(value) { prefs.edit().putString(KEY_SERVER_TOKEN, value.trim()).apply() }

    /**
     * Name of the TTS voice chosen in settings or voice mode.
     */
    var ttsVoice: String
        get() = prefs.getString(KEY_TTS_VOICE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_TTS_VOICE, value).apply()
        }

    var ttsSpeechRate: Float
        get() = prefs.getFloat(KEY_TTS_SPEECH_RATE, 1.0f)
        set(value) { prefs.edit().putFloat(KEY_TTS_SPEECH_RATE, value).apply() }

    var ttsPitch: Float
        get() = prefs.getFloat(KEY_TTS_PITCH, 1.0f)
        set(value) { prefs.edit().putFloat(KEY_TTS_PITCH, value).apply() }

    var autoSpeakReplies: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SPEAK, true)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_SPEAK, value).apply() }

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "Sir") ?: "Sir"
        set(value) { prefs.edit().putString(KEY_USER_NAME, value.trim()).apply() }

    var aiPersonality: String
        get() = prefs.getString(KEY_AI_PERSONALITY, "Iron Man JARVIS") ?: "Iron Man JARVIS"
        set(value) { prefs.edit().putString(KEY_AI_PERSONALITY, value).apply() }

    var modelName: String
        get() = prefs.getString(KEY_MODEL_NAME, "gemma4-e4b") ?: "gemma4-e4b"
        set(value) { prefs.edit().putString(KEY_MODEL_NAME, value).apply() }

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 0.7f)
        set(value) { prefs.edit().putFloat(KEY_TEMPERATURE, value).apply() }

    /** When enabled, chat replies are generated entirely on this Android device. */
    var onDeviceInferenceEnabled: Boolean
        get() = prefs.getBoolean(KEY_ON_DEVICE_INFERENCE, false)
        set(value) { prefs.edit().putBoolean(KEY_ON_DEVICE_INFERENCE, value).apply() }

    /** App-private absolute path to the user-imported LiteRT-LM model file. */
    var onDeviceModelPath: String
        get() = prefs.getString(KEY_ON_DEVICE_MODEL_PATH, "") ?: ""
        set(value) { prefs.edit().putString(KEY_ON_DEVICE_MODEL_PATH, value.trim()).apply() }

    /**
     * Voice Match: Restricts wake-word activation exclusively to the enrolled user's voice.
     */
    var voiceMatchEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_MATCH_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_VOICE_MATCH_ENABLED, value).apply()
        }

    /**
     * Cosine similarity threshold for speaker verification (0.60 to 0.90, default 0.72).
     */
    var voiceMatchThreshold: Float
        get() = prefs.getFloat(KEY_VOICE_MATCH_THRESHOLD, 0.72f)
        set(value) {
            prefs.edit().putFloat(KEY_VOICE_MATCH_THRESHOLD, value).apply()
        }

    val isVoiceProfileEnrolled: Boolean
        get() = prefs.getString(KEY_MASTER_VOICEPRINT, "").orEmpty().isNotBlank()

    fun saveVoiceProfile(embedding: FloatArray) {
        val encoded = embedding.joinToString(",")
        prefs.edit()
            .putString(KEY_MASTER_VOICEPRINT, encoded)
            .putBoolean(KEY_VOICE_MATCH_ENABLED, true)
            .apply()
    }

    fun getVoiceProfile(): FloatArray? {
        val raw = prefs.getString(KEY_MASTER_VOICEPRINT, null) ?: return null
        if (raw.isBlank()) return null
        return try {
            val parts = raw.split(",")
            FloatArray(parts.size) { parts[it].toFloat() }
        } catch (e: Exception) {
            null
        }
    }

    fun clearVoiceProfile() {
        prefs.edit()
            .remove(KEY_MASTER_VOICEPRINT)
            .putBoolean(KEY_VOICE_MATCH_ENABLED, false)
            .apply()
    }

    companion object {
        const val DEFAULT_SERVER_IP = "127.0.0.1:8000"
        const val DEFAULT_SERVER_TOKEN = "jarvis_local_token"

        private const val PREFS_NAME = "jarvis_settings"
        private const val KEY_SERVER_IP = "server_ip"
        private const val KEY_SERVER_TOKEN = "server_token"
        private const val KEY_THEME = "theme_mode"

        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_WAKE = "wake_word_enabled"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_AUTO_SPEAK = "auto_speak"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_AI_PERSONALITY = "ai_personality"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_ON_DEVICE_INFERENCE = "on_device_inference"
        private const val KEY_ON_DEVICE_MODEL_PATH = "on_device_model_path"

        private const val KEY_VOICE_MATCH_ENABLED = "voice_match_enabled"
        private const val KEY_VOICE_MATCH_THRESHOLD = "voice_match_threshold"
        private const val KEY_MASTER_VOICEPRINT = "master_voiceprint_vector"
    }
}
