package com.example.myjarvice.wake

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt


/**
 * Manages low-latency 16kHz PCM audio streaming with a rolling circular buffer
 * for real-time acoustic analysis and Voice Match speaker verification.
 */
class AudioBufferRecorder(
    val sampleRate: Int = 16000,
    bufferSeconds: Float = 3.0f
) {
    private val bufferSizeSamples = (sampleRate * bufferSeconds).toInt()
    private val ringBuffer = ShortArray(bufferSizeSamples)
    private var writeHead = 0
    private val bufferLock = Any()

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)

    companion object {
        private const val TAG = "AudioBufferRecorder"
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    /**
     * Starts continuous background audio capture into the rolling circular buffer.
     * [onFrame] is an optional callback receiving each chunk of raw audio and its RMS level in dB.
     */
    @SuppressLint("MissingPermission")
    fun start(onFrame: ((ShortArray, Int, Float) -> Unit)? = null): Boolean {
        if (isRecording.get()) return true

        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        val readBufferSize = max(minBufSize, sampleRate / 10) // ~100ms chunks

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                readBufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to standard MIC source if VOICE_RECOGNITION fails
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    readBufferSize * 2
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord instance.")
                return false
            }

            audioRecord?.startRecording()
            isRecording.set(true)

            recordingThread = Thread({
                val chunk = ShortArray(readBufferSize)
                while (isRecording.get()) {
                    val read = audioRecord?.read(chunk, 0, chunk.size) ?: -1
                    if (read > 0) {
                        // Append to rolling circular buffer
                        synchronized(bufferLock) {
                            for (i in 0 until read) {
                                ringBuffer[writeHead] = chunk[i]
                                writeHead = (writeHead + 1) % bufferSizeSamples
                            }
                        }

                        // Calculate RMS Amplitude
                        val rmsDb = calculateRms(chunk, read)
                        onFrame?.invoke(chunk, read, rmsDb)
                    }
                }
            }, "JarvisAudioBufferThread").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }

            Log.i(TAG, "AudioBufferRecorder online and streaming at $sampleRate Hz.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}", e)
            stop()
            return false
        }
    }

    /**
     * Retrieves the most recent [durationMs] milliseconds of audio from the ring buffer.
     */
    fun getRecentAudio(durationMs: Int = 2000): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0f)).toInt()
        val result = ShortArray(numSamples)

        synchronized(bufferLock) {
            val startPos = (writeHead - numSamples + bufferSizeSamples * 2) % bufferSizeSamples
            for (i in 0 until numSamples) {
                result[i] = ringBuffer[(startPos + i) % bufferSizeSamples]
            }
        }
        return result
    }

    /**
     * Records a dedicated audio clip of [durationMs] length (e.g. for Voice Match training).
     */
    @SuppressLint("MissingPermission")
    suspend fun recordSample(
        durationMs: Int = 2000,
        onProgress: ((progress: Float, rmsDb: Float) -> Unit)? = null
    ): ShortArray = withContext(Dispatchers.IO) {
        val totalSamples = (sampleRate * (durationMs / 1000.0f)).toInt()
        val recorded = ShortArray(totalSamples)
        var samplesRecorded = 0

        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        val readSize = max(minBufSize, 1024)
        val chunk = ShortArray(readSize)

        var record: AudioRecord? = null
        try {
            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                readSize * 2
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    readSize * 2
                )
            }

            record.startRecording()
            val startTime = System.currentTimeMillis()

            while (samplesRecorded < totalSamples) {
                val toRead = min(chunk.size, totalSamples - samplesRecorded)
                val read = record.read(chunk, 0, toRead)
                if (read > 0) {
                    System.arraycopy(chunk, 0, recorded, samplesRecorded, read)
                    samplesRecorded += read

                    val progress = samplesRecorded.toFloat() / totalSamples
                    val rms = calculateRms(chunk, read)
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(progress, rms)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recording enrollment sample: ${e.message}", e)
        } finally {
            try {
                record?.stop()
                record?.release()
            } catch (ignored: Exception) {}
        }
        recorded
    }

    fun stop() {
        isRecording.set(false)
        try {
            recordingThread?.interrupt()
            recordingThread = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.i(TAG, "AudioBufferRecorder stopped.")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
    }

    private fun calculateRms(buffer: ShortArray, length: Int): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        for (i in 0 until length) {
            sum += buffer[i] * buffer[i]
        }
        val mean = sum / length
        val rms = sqrt(mean)
        return if (rms > 1.0) (20 * log10(rms)).toFloat() else 0f
    }
}
