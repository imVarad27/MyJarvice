package com.example.myjarvice.wake

import kotlin.math.abs
import kotlin.math.sqrt

/** Audio feedback for supervised setup; live wake and owner checks stay separate. */
object EnrollmentAudio {
    const val SAMPLE_RATE = 16_000
    const val FRAME_SAMPLES = 320 // 20 ms
    const val SPEECH_RMS = 250.0
    enum class Issue { TOO_QUIET, TOO_SHORT, CLIPPED }

    fun rms(samples: ShortArray, offset: Int = 0, count: Int = samples.size): Double {
        if (count <= 0) return 0.0
        var energy = 0.0
        for (i in offset until offset + count) energy += samples[i].toDouble() * samples[i]
        return sqrt(energy / count)
    }

    fun issue(samples: ShortArray): Issue? {
        val trimmed = VoiceprintMatcher.trimSilence(samples)
        if (trimmed.isEmpty()) return Issue.TOO_QUIET
        if (trimmed.count { abs(it.toInt()) >= 32_000 }.toDouble() / trimmed.size >= 0.03) return Issue.CLIPPED
        var voiced = 0
        for (start in samples.indices step FRAME_SAMPLES) {
            val count = minOf(FRAME_SAMPLES, samples.size - start)
            if (rms(samples, start, count) >= SPEECH_RMS) voiced += count
        }
        return if (voiced < SAMPLE_RATE * 2 / 5) Issue.TOO_SHORT else null
    }
}

/** Wait for speech, then allow a natural pause between the two wake words. */
class EnrollmentEndpoint {
    private var voicedSamples = 0
    private var quietSamples = 0

    fun accept(frame: ShortArray, count: Int): Boolean {
        if (EnrollmentAudio.rms(frame, count = count) >= EnrollmentAudio.SPEECH_RMS) {
            voicedSamples += count
            quietSamples = 0
        } else {
            quietSamples += count
        }
        return voicedSamples >= EnrollmentAudio.SAMPLE_RATE * 2 / 5 &&
            quietSamples >= EnrollmentAudio.SAMPLE_RATE * 9 / 10
    }
}
