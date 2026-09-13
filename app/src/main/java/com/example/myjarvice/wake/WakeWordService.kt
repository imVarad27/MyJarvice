package com.example.myjarvice.wake

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myjarvice.MainActivity
import com.example.myjarvice.data.SettingsStore
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/** One offline recorder, explicitly started while the app is visible. */
class WakeWordService : Service(), RecognitionListener {
    companion object {
        const val ACTION_START = "ACTION_START_WAKE_WORD"
        const val ACTION_STOP = "ACTION_STOP_WAKE_WORD"
        const val CHANNEL_ID = "JarvisWakeChannel"
        const val NOTIFICATION_ID = 1001
        @Volatile private var requested = false
        fun start(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
            requested = true
            try {
                ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java).setAction(ACTION_START))
            } catch (e: RuntimeException) {
                requested = false
                WakeEvents.status.value = "Open Jarvis to enable hands-free voice"
                Log.w("WakeWordService", "Microphone startup restricted", e)
            }
        }
        fun stop(context: Context) {
            requested = false
            if (WakeEvents.running.value) context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var initialization: Job? = null
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speech: SpeechService? = null
    private var capturing = false
    private var promoted = false
    private var cooldownUntil = 0L
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Hey Jarvis", NotificationManager.IMPORTANCE_LOW))
        try {
            startForeground(NOTIFICATION_ID, notification("Starting hands-free voice"))
            promoted = true
            WakeEvents.running.value = true
        } catch (e: RuntimeException) {
            WakeEvents.status.value = "Microphone permission unavailable"
            Log.w("WakeWordService", "Foreground startup failed", e)
            stopSelf()
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SettingsStore(this).wakeWordEnabled = false
            requested = false
        }
        if (!requested || !promoted) { stopSelf(); return START_NOT_STICKY }
        if (initialization == null) initialization = scope.launch {
            try {
                updateStatus(if (WakeModelStore.ready(this@WakeWordService)) "Loading wake listener" else "Downloading wake model (40 MB)…")
                val path = withContext(Dispatchers.IO) { WakeModelStore.prepare(this@WakeWordService).absolutePath }
                ensureActive()
                var loaded: Model? = null
                try {
                    withContext(Dispatchers.IO) { loaded = Model(path) }
                    model = loaded
                    loaded = null
                } finally { loaded?.close() }
                recognizer = Recognizer(model, 16000f, "[\"hey jarvis\", \"hi jarvis\", \"okay jarvis\", \"ok jarvis\", \"[unk]\"]")
                speech = SpeechService(recognizer, 16000f)
                WakeEvents.microphoneBusy.collect { busy -> if (busy) pauseCapture() else resumeCapture() }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                updateStatus("Wake setup failed. Check internet, then toggle off and on.")
                Log.e("WakeWordService", "Wake setup failed", e)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
    private fun pauseCapture() {
        if (capturing) speech?.stop()
        capturing = false
        WakeEvents.captureReleased.value = true
        updateStatus("Paused while Jarvis is in use")
    }
    private fun resumeCapture() {
        if (!requested || WakeEvents.microphoneBusy.value || capturing || SystemClock.elapsedRealtime() < cooldownUntil) return
        recognizer?.reset()
        capturing = speech?.startListening(this) == true
        WakeEvents.captureReleased.value = !capturing
        updateStatus(if (capturing) "Listening for Hey Jarvis" else "Microphone unavailable. Toggle off and on to retry.")
    }
    private fun accept(hypothesis: String?) {
        val data = runCatching { JSONObject(hypothesis.orEmpty()) }.getOrNull() ?: return
        val text = data.optString("partial", data.optString("text"))
        if (!capturing || WakeEvents.microphoneBusy.value || !WakePhrase.matches(text) || SystemClock.elapsedRealtime() < cooldownUntil) return
        cooldownUntil = SystemClock.elapsedRealtime() + 4000
        pauseCapture()
        Log.i("WakeWordService", "Wake phrase detected")
        if (WakeEvents.appVisible.value) WakeEvents.voiceTrigger.value = true
        else if (Settings.canDrawOverlays(this)) runCatching { startActivity(voiceIntent()) }
        updateStatus("Hey! Tap to speak to Jarvis")
        scope.launch { delay(4200); resumeCapture() }
    }
    override fun onPartialResult(hypothesis: String?) = accept(hypothesis)
    override fun onResult(hypothesis: String?) = accept(hypothesis)
    override fun onFinalResult(hypothesis: String?) = Unit
    override fun onTimeout() { pauseCapture(); resumeCapture() }
    override fun onError(exception: Exception?) {
        pauseCapture()
        Log.w("WakeWordService", "Wake microphone interrupted", exception)
        scope.launch { delay(2000); resumeCapture() }
    }
    private fun voiceIntent() = Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(MainActivity.EXTRA_START_VOICE, true)
    private fun notification(text: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Hey Jarvis").setContentText(text)
            .setContentIntent(PendingIntent.getActivity(this, 0, voiceIntent(), flags))
            .addAction(0, "Stop listening", PendingIntent.getService(this, 1, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP), flags))
            .setOnlyAlertOnce(true).setOngoing(true).build()
    }
    private fun updateStatus(text: String) {
        WakeEvents.status.value = text
        if (promoted) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        speech?.stop()
        speech?.shutdown()
        recognizer?.close()
        model?.close()
        WakeEvents.captureReleased.value = true
        WakeEvents.running.value = false
        if (!requested) WakeEvents.status.value = "Off"
        super.onDestroy()
    }
}
