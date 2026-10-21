package com.example.myjarvice.ui.voice

internal data class VoicePresentation(val title: String, val detail: String, val canRetry: Boolean,
    val micLabel: String, val micDescription: String)

/** Display policy only. Recognition status is preserved verbatim, not classified by magic phrases. */
internal fun voicePresentation(listening: Boolean, speaking: Boolean, thinking: Boolean, muted: Boolean,
    recognitionStatus: String): VoicePresentation {
    val retry = !muted && !listening && !speaking && !thinking
    val title = when {
        thinking -> "Thinking…"
        speaking -> "Speaking…"
        muted -> "Microphone muted"
        recognitionStatus.isNotBlank() -> recognitionStatus
        listening -> "Listening…"
        else -> "Ready when you are"
    }
    val detail = when {
        muted && (thinking || speaking) -> "Microphone muted"
        muted -> "Unmute to continue the conversation."
        thinking -> "Preparing your reply."
        speaking -> "Jarvis is reading the reply aloud."
        listening -> "Speak naturally. Your words appear below."
        else -> "Tap the microphone to start listening again."
    }
    return VoicePresentation(title, detail, retry,
        if (muted) "Unmute" else if (retry) "Listen" else "Mute",
        if (muted) "Unmute microphone" else if (retry) "Start listening" else "Mute microphone")
}

internal fun normalizedVoiceLevel(level: Float): Float =
    if (level.isFinite()) ((level + 2f) / 12f).coerceIn(0f, 1f) else 0f
