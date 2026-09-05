package com.example.myjarvice.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

/**
 * Programmatic sci-fi synthesizer generating Iron Man / Arc Reactor UI sound cues.
 */
object JarvisSoundFx {
    private const val TAG = "JarvisSoundFx"
    private const val SAMPLE_RATE = 44100

    /**
     * Arc Reactor Wake Word / Listening Activation Chime (Two-tone electronic frequency sweep).
     */
    suspend fun playWakeChime() = withContext(Dispatchers.Default) {
        try {
            val durationMs = 160
            val numSamples = (SAMPLE_RATE * durationMs) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / numSamples
                // Frequency sweep from 440Hz to 880Hz with envelope fade
                val freq = 440.0 + (progress * 440.0)
                val envelope = sin(progress * Math.PI)
                val sample = (sin(2.0 * Math.PI * freq * t) * envelope * Short.MAX_VALUE * 0.45).toInt()
                buffer[i] = sample.toShort()
            }

            playPcmBuffer(buffer)
        } catch (e: Exception) {
            Log.w(TAG, "playWakeChime failed: ${e.message}")
        }
    }

    /**
     * Command Execution Confirmation Ping (Harmonic triad ping).
     */
    suspend fun playSuccessChime() = withContext(Dispatchers.Default) {
        try {
            val durationMs = 200
            val numSamples = (SAMPLE_RATE * durationMs) / 1000
            val buffer = ShortArray(numSamples)

            val f1 = 523.25 // C5
            val f2 = 659.25 // E5
            val f3 = 783.99 // G5

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / numSamples
                val envelope = (1.0 - progress) * sin(progress * Math.PI)
                val sample = ((sin(2.0 * Math.PI * f1 * t) + sin(2.0 * Math.PI * f2 * t) + sin(2.0 * Math.PI * f3 * t)) / 3.0 * envelope * Short.MAX_VALUE * 0.5).toInt()
                buffer[i] = sample.toShort()
            }

            playPcmBuffer(buffer)
        } catch (e: Exception) {
            Log.w(TAG, "playSuccessChime failed: ${e.message}")
        }
    }

    /**
     * Reminder / Notification Alert Pulse.
     */
    suspend fun playAlertChime() = withContext(Dispatchers.Default) {
        try {
            val durationMs = 280
            val numSamples = (SAMPLE_RATE * durationMs) / 1000
            val buffer = ShortArray(numSamples)

            val f = 880.0 // A5

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / numSamples
                val envelope = sin(progress * Math.PI * 3.0).coerceAtLeast(0.0) * (1.0 - progress)
                val sample = (sin(2.0 * Math.PI * f * t) * envelope * Short.MAX_VALUE * 0.55).toInt()
                buffer[i] = sample.toShort()
            }

            playPcmBuffer(buffer)
        } catch (e: Exception) {
            Log.w(TAG, "playAlertChime failed: ${e.message}")
        }
    }

    private fun playPcmBuffer(buffer: ShortArray) {
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.play()
    }
}
