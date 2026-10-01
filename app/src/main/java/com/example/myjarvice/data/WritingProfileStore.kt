package com.example.myjarvice.data

import android.content.Context

/** Preferences stay on the phone. Only the chosen draft prompt can include them. */
class WritingProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_writing_profile", Context.MODE_PRIVATE)

    fun load(): WritingProfile = WritingProfile(
        enabled = prefs.getBoolean("enabled", false),
        tone = runCatching { DraftTone.valueOf(prefs.getString("tone", "").orEmpty()) }.getOrDefault(DraftTone.NATURAL),
        length = runCatching { DraftLength.valueOf(prefs.getString("length", "").orEmpty()) }.getOrDefault(DraftLength.BALANCED),
        contractions = prefs.getBoolean("contractions", true),
        emoji = prefs.getBoolean("emoji", false),
        signOff = prefs.getString("sign_off", "").orEmpty(),
        avoidPhrases = prefs.getString("avoid_phrases", "").orEmpty()
    ).normalized()

    fun save(profile: WritingProfile) {
        val value = profile.normalized()
        prefs.edit().putBoolean("enabled", value.enabled).putString("tone", value.tone.name)
            .putString("length", value.length.name).putBoolean("contractions", value.contractions)
            .putBoolean("emoji", value.emoji).putString("sign_off", value.signOff)
            .putString("avoid_phrases", value.avoidPhrases).apply()
    }

    fun reset() { prefs.edit().clear().apply() }
}
