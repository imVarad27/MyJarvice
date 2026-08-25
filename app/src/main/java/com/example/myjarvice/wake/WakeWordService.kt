package com.example.myjarvice.wake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myjarvice.MainActivity
import com.example.myjarvice.data.SettingsStore
import java.util.Locale


class WakeWordService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START_WAKE_WORD"
        const val ACTION_STOP = "ACTION_STOP_WAKE_WORD"
        const val ACTION_WAKE_WORD_DETECTED = "com.example.myjarvice.WAKE_WORD_DETECTED"
        const val CHANNEL_ID = "JarvisWakeChannel"
        const val NOTIFICATION_ID = 1001

        private const val TAG = "WakeWordService"

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var audioRecorder: AudioBufferRecorder? = null
    private var isListening = false
    private lateinit var settings: SettingsStore

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = buildNotification()
                startForeground(NOTIFICATION_ID, notification)
                startWakeWordListening()
            }
            ACTION_STOP -> {
                stopWakeWordListening()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun startWakeWordListening() {
        if (isListening || !SpeechRecognizer.isRecognitionAvailable(this)) return

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Sentinel ready and listening for 'Hey Jarvis'...")
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        Log.d(TAG, "Speech sentinel error code: $error. Re-arming listener...")
                        isListening = false
                        handler.postDelayed({
                            startWakeWordListening()
                        }, 400)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognized = matches[0].lowercase(Locale.getDefault())
                            Log.d(TAG, "Sentinel heard: $recognized")
                            checkAndTriggerWake(recognized)
                        }
                        isListening = false
                        handler.postDelayed({
                            startWakeWordListening()
                        }, 200)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognized = matches[0].lowercase(Locale.getDefault())
                            checkAndTriggerWake(recognized)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            isListening = true
            speechRecognizer?.startListening(recognizerIntent)
            Log.d(TAG, "Jarvis Sentinel active and listening for 'Hey Jarvis'...")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting sentinel recognizer: ${e.message}")
            isListening = false
        }
    }

    private fun checkAndTriggerWake(text: String) {
        val clean = text.replace("'", "").replace(".", "").trim()
        if (clean.contains("jarvis") || clean.contains("jarvice") || clean.contains("hey jarvis") || clean.contains("hi jarvis") || clean.contains("ok jarvis")) {
            Log.i(TAG, "⚡ WAKE PHRASE DETECTED: '$clean' -> Launching JARVIS ⚡")
            broadcastWakeWordDetected()
        }
    }

    private fun broadcastWakeWordDetected() {
        WakeEvents.voiceTrigger.value = true
        val broadcastIntent = Intent(ACTION_WAKE_WORD_DETECTED)
        sendBroadcast(broadcastIntent)

        // Bring JARVIS Voice HUD to the foreground
        try {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_START_VOICE, true)
            }
            startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching MainActivity from wake: ${e.message}")
        }
    }

    private fun stopWakeWordListening() {
        isListening = false
        handler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (ignored: Exception) {}
        speechRecognizer = null
    }



    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVICE Sentinel Mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background continuous 'Jarvis' wake word detection"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS Sentinel Mode Active")
            .setContentText("Listening hands-free for 'Jarvis'...")

            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopWakeWordListening()
    }
}
