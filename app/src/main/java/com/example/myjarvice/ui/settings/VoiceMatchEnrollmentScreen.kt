package com.example.myjarvice.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.ui.JarvisBrandMark
import com.example.myjarvice.ui.JarvisPageHeader
import com.example.myjarvice.wake.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

private const val SAMPLE_COUNT = 3
private enum class SetupPhase { READY, PREPARING, COUNTDOWN, LISTENING, CHECKING, COMPLETE }

@Composable
fun VoiceMatchEnrollmentScreen(onFinished: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    val recorder = remember { AudioBufferRecorder() }
    val samples = remember { mutableStateListOf<ShortArray>() }
    var job by remember { mutableStateOf<Job?>(null) }
    var attempted by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(SetupPhase.READY) }
    var countdown by remember { mutableIntStateOf(2) }
    var level by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Tap Start. Say ‘Hey Jarvis’ each time Speak now appears.") }
    val busy = phase !in listOf(SetupPhase.READY, SetupPhase.COMPLETE)
    val colors = MaterialTheme.colorScheme

    fun pause() {
        job?.cancel()
        recorder.stop()
        if (phase != SetupPhase.COMPLETE) {
            phase = SetupPhase.READY
            status = "Paused. Your completed samples are kept while this screen stays open."
        }
    }

    DisposableEffect(lifecycle) {
        WakeEvents.setMicrophoneBusy("enrollment", true)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            job?.cancel()
            recorder.stop()
            lifecycle.removeObserver(observer)
            WakeEvents.setMicrophoneBusy("enrollment", false)
        }
    }

    fun start() {
        if (job != null) return
        attempted = true
        phase = SetupPhase.PREPARING
        status = if (WakeModelStore.ready(context)) "Getting voice setup ready…"
            else "Preparing offline recognition. The first setup downloads about 40 MB."
        job = scope.launch {
            val validator = WakeEnrollmentValidator()
            try {
                validator.prepare(context)
                if (withTimeoutOrNull(3500) { WakeEvents.captureReleased.first { it } } != true) {
                    status = "The microphone is busy. Close any voice call or recording, then tap Try again."
                    return@launch
                }
                while (samples.size < SAMPLE_COUNT) {
                    phase = SetupPhase.COUNTDOWN
                    status = if (samples.isEmpty()) "Use your normal voice. Wait for Speak now."
                        else "Got it. Get ready to say it once more."
                    level = 0f
                    for (second in 2 downTo 1) { countdown = second; delay(1000) }
                    val audio = recorder.recordSample(durationMs = 6000, stopAfterSpeech = true,
                        onReady = {
                            phase = SetupPhase.LISTENING
                            status = "Say ‘Hey Jarvis’ once, then pause. No need to speak loudly."
                        },
                        onProgress = { _, rms -> level = ((rms - 35f) / 40f).coerceIn(0f, 1f) })
                    phase = SetupPhase.CHECKING
                    status = "Checking this sample…"
                    val issue = withContext(Dispatchers.Default) { EnrollmentAudio.issue(audio) }
                    if (issue != null) {
                        status = when (issue) {
                            EnrollmentAudio.Issue.TOO_QUIET -> "The microphone picked up very little sound. Hold the phone closer and check that the mic is uncovered."
                            EnrollmentAudio.Issue.TOO_SHORT -> "That was a little short. Say both words—‘Hey Jarvis’—after Speak now appears."
                            EnrollmentAudio.Issue.CLIPPED -> "That was too loud for the microphone. Speak normally or move the phone a little farther away."
                        }
                        break
                    }
                    val result = validator.check(audio)
                    if (!result.accepted) {
                        status = if (result.heard.isBlank() || result.heard.contains("[unk]"))
                            "I heard sound but couldn't make out the phrase. Say ‘Hey Jarvis’ once, with a short pause afterward."
                        else "I couldn't confirm both words clearly. Try ‘Hey Jarvis’ in your normal voice."
                        break
                    }
                    val cleanAudio = withContext(Dispatchers.Default) { VoiceprintMatcher.trimSilence(audio) }
                    val candidate = samples.toList() + listOf(cleanAudio)
                    val profile = withContext(Dispatchers.Default) { VoiceprintMatcher.enrollMasterProfile(candidate) }
                    val consistent = withContext(Dispatchers.Default) {
                        candidate.all { VoiceprintMatcher.verify(profile, it, settings.voiceMatchThreshold).first }
                    }
                    if (!consistent) {
                        status = "This sample sounded different. Keep the same distance and use your normal voice. Earlier samples are kept."
                        break
                    }
                    if (candidate.size == SAMPLE_COUNT && !settings.saveVoiceProfile(profile)) {
                        status = "Couldn't save this sample. Please try it again; your earlier samples are kept."
                        break
                    }
                    samples.add(cleanAudio)
                }
                if (samples.size == SAMPLE_COUNT) {
                    phase = SetupPhase.COMPLETE
                    status = "Your voice profile is ready."
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                status = when (phase) {
                    SetupPhase.PREPARING -> "Couldn't prepare voice setup. Check your connection and tap Try again. Your existing profile is unchanged."
                    SetupPhase.CHECKING -> "Couldn't check this sample. Tap Try again; your earlier samples are kept."
                    else -> "Couldn't record this sample. Check microphone access and close other recording apps, then try again."
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { validator.close() }
                level = 0f
                if (phase != SetupPhase.COMPLETE) phase = SetupPhase.READY
                job = null
            }
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        JarvisPageHeader("Set up your voice", "One start · three short recordings", onBack)
        Spacer(Modifier.height(16.dp))
        JarvisBrandMark()
        Spacer(Modifier.height(24.dp))
        Text(if (phase == SetupPhase.COMPLETE) "You're all set" else "Let Jarvis learn your voice",
            style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text("Hold the phone comfortably in front of you and speak normally. Setup audio stays on this phone.",
            color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(SAMPLE_COUNT) { index ->
                val done = index < samples.size
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    color = if (done) colors.primaryContainer else colors.surfaceContainerHigh) {
                    Text(if (done) "✓ Saved" else "${index + 1}", textAlign = TextAlign.Center,
                        color = if (done) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when (phase) {
                    SetupPhase.COUNTDOWN -> "Ready in $countdown"
                    SetupPhase.LISTENING -> "Speak now"
                    SetupPhase.PREPARING -> "Getting ready"
                    SetupPhase.CHECKING -> "Checking"
                    SetupPhase.COMPLETE -> "Voice profile ready"
                    SetupPhase.READY -> if (samples.isEmpty()) "Say it three times" else "Let's finish your setup"
                }, style = MaterialTheme.typography.titleMedium, color = colors.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (phase != SetupPhase.COMPLETE) {
                    Text("Hey Jarvis", style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(vertical = 20.dp))
                }
                when (phase) {
                    SetupPhase.LISTENING -> {
                        LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth()
                            .semantics { contentDescription = "Microphone level" })
                        Text("Take your time · up to 6 seconds", style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    }
                    SetupPhase.PREPARING, SetupPhase.CHECKING -> CircularProgressIndicator(Modifier.size(28.dp))
                    else -> Unit
                }
                Text(status, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        Spacer(Modifier.height(24.dp))
        if (phase == SetupPhase.COMPLETE) {
            Text("Jarvis will check for ‘Hey Jarvis’ and compare it with your voice profile. Voice matching is a convenience feature, not a secure identity check.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center)
            Button(onClick = onFinished, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp)) { Text("Done") }
        } else if (busy) {
            OutlinedButton(onClick = { pause() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Pause setup") }
        } else {
            if (samples.isNotEmpty()) Text("${samples.size} of $SAMPLE_COUNT saved · continue with the next recording",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp))
            Button(onClick = { start() }, enabled = job == null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (samples.isNotEmpty()) "Continue setup" else if (attempted) "Try again" else "Start")
            }
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) { Text("Set up later") }
        }
    }
}
