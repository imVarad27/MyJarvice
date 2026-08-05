package com.example.myjarvice.wake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myjarvice.MainActivity
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Always-on foreground service that listens for the "jarvis" wake word using the
 * fully-offline Vosk engine (no API key). The recognizer's vocabulary is limited
 * to "jarvis" for accuracy and low CPU. On detection it brings JARVIC to the
 * foreground in voice mode (needs "display over other apps" for the pop-up).
 */
class WakeWordService : Service() {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var lastTriggerMs = 0L

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) = checkForWake(hypothesis)
        override fun onResult(hypothesis: String?) = checkForWake(hypothesis)
        override fun onFinalResult(hypothesis: String?) = checkForWake(hypothesis)
        override fun onError(e: Exception?) { Log.e(TAG, "Vosk error: ${e?.message}") }
        override fun onTimeout() {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        loadModelAndListen()
        return START_STICKY
    }

    private fun startInForeground() {
        val channelId = "jarvic_wake"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "JARVIC Wake Word", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("JARVIC is listening")
            .setContentText("Say \"Jarvis\" to activate")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun loadModelAndListen() {
        if (speechService != null) return
        // Unpacks the bundled model from assets/model-en-us to internal storage (first run only).
        StorageService.unpack(
            this, "model-en-us", "vosk-model",
            { loadedModel ->
                model = loadedModel
                startRecognition()
            },
            { exception ->
                Log.e(TAG, "Failed to unpack Vosk model: ${exception.message}", exception)
                stopSelf()
            }
        )
    }

    private fun startRecognition() {
        try {
            // Restrict vocabulary to the wake word for reliable, low-cost spotting.
            val recognizer = Recognizer(model, 16000.0f, "[\"jarvis\", \"[unk]\"]")
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(listener)
            Log.i(TAG, "Vosk started — listening for \"jarvis\".")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Vosk recognition: ${e.message}", e)
            stopSelf()
        }
    }

    private fun checkForWake(hypothesis: String?) {
        if (hypothesis == null || !hypothesis.contains("jarvis", ignoreCase = true)) return
        val now = System.currentTimeMillis()
        if (now - lastTriggerMs < 3000) return   // debounce repeated hits
        lastTriggerMs = now
        onWakeWordDetected()
    }

    private fun onWakeWordDetected() {
        Log.i(TAG, "Wake word detected — launching JARVIC voice mode.")
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_START_VOICE, true)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Vosk: ${e.message}")
        }
        speechService = null
        model?.close()
        model = null
    }

    companion object {
        private const val TAG = "WakeWordService"
        private const val NOTIF_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
}
