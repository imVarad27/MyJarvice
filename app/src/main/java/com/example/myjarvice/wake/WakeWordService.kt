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
import com.example.myjarvice.AssistantPopupActivity
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myjarvice.MainActivity
import com.example.myjarvice.data.SettingsStore
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/** One offline recorder, explicitly started while the app is visible. */
class WakeWordService : Service() {
    companion object {
        const val ACTION_START = "ACTION_START_WAKE_WORD"
        const val ACTION_STOP = "ACTION_STOP_WAKE_WORD"
        const val CHANNEL_ID = "JarvisWakeChannel"
        const val NOTIFICATION_ID = 1001
        private const val WAKE_GRAMMAR = "[\"hey jarvis\", \"[unk]\"]"
        private const val STABLE_PARTIAL_FRAMES = 2
        @Volatile private var requested = false
        fun start(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
            val settings = SettingsStore(context)
            if (settings.assistantPaused) {
                requested = false
                WakeEvents.status.value = "Jarvis is paused"
                if (WakeEvents.running.value) context.stopService(Intent(context, WakeWordService::class.java))
                return
            }
            if (!settings.isVoiceProfileEnrolled || !settings.voiceMatchEnabled) {
                requested = false
                WakeEvents.status.value = "Set up and enable your personal voice profile first"
                if (WakeEvents.running.value) context.stopService(Intent(context, WakeWordService::class.java))
                return
            }
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
    private var recorder: AudioBufferRecorder? = null
    private var capturing = false
    @Volatile private var matchInProgress = false
    private var promoted = false
    private var cooldownUntil = 0L
    private var partialWakeHits = 0
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
        if (initialization == null) {
            initialization = scope.launch {
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
                    // A phrase grammar is more reliable than free-form transcription for the
                    // user's short wake phrase. [unk] remains available so unrelated speech is
                    // rejected instead of being forced into "hey jarvis".
                    recognizer = Recognizer(model, 16000f, WAKE_GRAMMAR).apply { setWords(true) }
                    recorder = AudioBufferRecorder(sampleRate = 16000, bufferSeconds = 3.0f)
                    WakeEvents.microphoneBusy.collect { busy -> if (busy) pauseCapture() else resumeCapture() }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    updateStatus("Wake setup failed. Check internet, then toggle off and on.")
                    Log.e("WakeWordService", "Wake setup failed", e)
                    stopSelf()
                }
            }
        } else if (!capturing && !matchInProgress && !WakeEvents.microphoneBusy.value) {
            // A foreground retry recovers from Android denying microphone access during
            // an early cold-start service launch; the model stays loaded.
            scope.launch {
                delay(250)
                resumeCapture()
            }
        }
        return START_NOT_STICKY
    }
    private fun pauseCapture() {
        val released = if (capturing) recorder?.stop() != false else true
        capturing = false
        WakeEvents.captureReleased.value = released
        updateStatus("Paused while Jarvis is in use")
    }
    private fun resumeCapture() {
        if (!requested || WakeEvents.microphoneBusy.value || capturing || matchInProgress || SystemClock.elapsedRealtime() < cooldownUntil) return
        if (SettingsStore(this).assistantPaused) {
            updateStatus("Jarvis is paused")
            stopSelf()
            return
        }
        if (recorder?.isReleased() == false) {
            scope.launch { delay(200); resumeCapture() }
            return
        }
        WakeEvents.captureReleased.value = true
        recognizer?.reset()
        partialWakeHits = 0
        recorder?.clear()
        if (!SettingsStore(this).voiceMatchEnabled || !SettingsStore(this).isVoiceProfileEnrolled) {
            updateStatus("Voice profile required for hands-free wake")
            stopSelf()
            return
        }
        capturing = recorder?.start(onError = { error ->
            scope.launch {
                pauseCapture()
                Log.w("WakeWordService", "Wake recorder interrupted", error)
                delay(2000)
                resumeCapture()
            }
        }) { audio, length, _ ->
            val localRecognizer = recognizer ?: return@start
            if (localRecognizer.acceptWaveForm(audio, length)) {
                partialWakeHits = 0
                acceptFinal(localRecognizer.result)
            } else {
                acceptPartial(localRecognizer.partialResult)
            }
        } == true
        WakeEvents.captureReleased.value = !capturing
        updateStatus(if (capturing) "Listening for Hey Jarvis" else "Microphone unavailable. Toggle off and on to retry.")
    }
    private fun acceptFinal(hypothesis: String?) {
        val data = runCatching { JSONObject(hypothesis.orEmpty()) }.getOrNull() ?: return
        val text = data.optString("text")
        val recognizedWords = data.optJSONArray("result") ?: return
        val words = (0 until recognizedWords.length()).map { index ->
            val word = recognizedWords.getJSONObject(index)
            word.optString("word") to word.optDouble("conf", 0.0)
        }
        if (!WakePhrase.matches(text)) return
        if (!WakePhrase.confidentWakeCandidate(words)) {
            Log.d("WakeWordService", "Exact wake candidate rejected by phrase confidence gate")
            return
        }
        beginVoiceVerification()
    }

    /**
     * Vosk can hold a short phrase as a partial result for several seconds when there is
     * background noise. Two consecutive exact partials let the private voice check start
     * promptly without accepting a single unstable recognition frame.
     */
    private fun acceptPartial(hypothesis: String?) {
        if (!capturing || matchInProgress || WakeEvents.microphoneBusy.value ||
            SystemClock.elapsedRealtime() < cooldownUntil
        ) return
        val partial = runCatching {
            JSONObject(hypothesis.orEmpty()).optString("partial")
        }.getOrDefault("")
        partialWakeHits = if (WakePhrase.matches(partial)) partialWakeHits + 1 else 0
        if (partialWakeHits >= STABLE_PARTIAL_FRAMES) {
            partialWakeHits = 0
            beginVoiceVerification()
        }
    }

    private fun beginVoiceVerification() {
        if (!capturing || matchInProgress || WakeEvents.microphoneBusy.value ||
            SystemClock.elapsedRealtime() < cooldownUntil
        ) return
        matchInProgress = true
        val wakeAudio = recorder?.getRecentAudio(2200) ?: ShortArray(0)
        scope.launch {
            pauseCapture()
            verifyAndActivate(wakeAudio)
        }
    }

    private suspend fun verifyAndActivate(wakeAudio: ShortArray) {
        val settingsStore = SettingsStore(this)
        val profile = settingsStore.getVoiceProfile()
        if (!settingsStore.voiceMatchEnabled || profile == null) {
            WakeEvents.ownerVerified.value = false
            updateStatus("Voice profile required — wake ignored")
            finishMatch(delayMs = 1800)
            return
        }

        updateStatus("Checking your voice…")
        val (matched, score) = withContext(Dispatchers.Default) {
            if (VoiceprintMatcher.isUsableVoiceSample(wakeAudio)) {
                VoiceprintMatcher.verify(profile, wakeAudio, settingsStore.voiceMatchThreshold)
            } else Pair(false, 0f)
        }
        WakeEvents.lastVoiceMatchScore.value = score
        if (matched && WakeActivationPolicy.permits(profile != null, settingsStore.voiceMatchEnabled, score, settingsStore.voiceMatchThreshold)) {
            activateWake(ownerVerified = true)
        } else {
            WakeEvents.ownerVerified.value = false
            cooldownUntil = SystemClock.elapsedRealtime() + 1800
            updateStatus("Voice not recognized — tap the notification to open Jarvis")
            finishMatch(delayMs = 1900)
        }
    }

    private fun activateWake(ownerVerified: Boolean) {
        if (!requested || SettingsStore(this).assistantPaused) return
        cooldownUntil = SystemClock.elapsedRealtime() + 4000
        WakeEvents.ownerVerified.value = ownerVerified
        Log.i("WakeWordService", "Wake phrase accepted; ownerVerified=$ownerVerified")
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (!locked && (WakeEvents.appVisible.value || Settings.canDrawOverlays(this))) {
            runCatching { startActivity(popupIntent(verified = true)) }
                .onFailure { Log.w("WakeWordService", "Popup launch restricted", it) }
        }
        updateStatus(if (locked) "Voice matched — unlock your phone and tap to open Jarvis"
            else "Voice matched — tap if the popup did not open")
        finishMatch(delayMs = 4200)
    }

    private fun finishMatch(delayMs: Long) {
        scope.launch {
            delay(delayMs)
            matchInProgress = false
            resumeCapture()
        }
    }
    private fun popupIntent(verified: Boolean = false) = Intent(this, AssistantPopupActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(AssistantPopupActivity.EXTRA_VERIFIED_WAKE, verified)
    private fun notification(text: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Hey Jarvis").setContentText(text)
            .setContentIntent(PendingIntent.getActivity(this, 0, popupIntent(), flags))
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
        recorder?.stop()
        recognizer?.close()
        model?.close()
        WakeEvents.ownerVerified.value = false
        WakeEvents.captureReleased.value = true
        WakeEvents.running.value = false
        if (!requested) WakeEvents.status.value = "Off"
        super.onDestroy()
    }
}
