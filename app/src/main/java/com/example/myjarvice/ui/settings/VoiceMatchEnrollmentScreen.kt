package com.example.myjarvice.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.data.SpeechManager
import com.example.myjarvice.theme.ArcGold
import com.example.myjarvice.theme.JarvisBlue
import com.example.myjarvice.theme.JarvisCyan
import com.example.myjarvice.wake.AudioBufferRecorder
import com.example.myjarvice.wake.VoiceprintMatcher
import kotlinx.coroutines.launch

private val ENROLLMENT_PROMPTS = listOf(
    "Hey Jarvis, systems online",
    "Hey Jarvis, status report",
    "Hey Jarvis, what's on my agenda?"
)

@Composable
fun VoiceMatchEnrollmentScreen(
    onFinished: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val speechManager = remember { SpeechManager(context) }
    val audioRecorder = remember { AudioBufferRecorder(sampleRate = 16000) }

    var currentStep by remember { mutableIntStateOf(0) } // 0, 1, 2, or 3 (completed)
    var isRecording by remember { mutableStateOf(false) }
    var recordingProgress by remember { mutableFloatStateOf(0f) }
    var micRmsLevel by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("Tap the Arc Core to begin calibration.") }

    val recordedSamples = remember { mutableStateListOf<ShortArray>() }

    val scheme = MaterialTheme.colorScheme

    // Pulsing animations
    val infiniteTransition = rememberInfiniteTransition(label = "VoiceMatchPulse")
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )

    val reactorScale = if (isRecording) {
        1.0f + (micRmsLevel / 100f).coerceIn(0f, 0.45f)
    } else {
        idlePulse
    }

    val glowColor by animateColorAsState(
        targetValue = if (currentStep == 3) ArcGold else if (isRecording) JarvisCyan else JarvisBlue,
        label = "glowColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = scheme.onBackground, fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Voice Match Calibration",
                    color = scheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Iron Man Biometric Voice Lock",
                    color = JarvisCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Step Progress Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 0 until 3) {
                val stepColor = when {
                    i < currentStep -> JarvisCyan
                    i == currentStep -> ArcGold
                    else -> scheme.outline.copy(alpha = 0.3f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(stepColor)
                )
            }
        }

        Spacer(Modifier.height(30.dp))

        if (currentStep < 3) {
            Text(
                "PHASE ${currentStep + 1} OF 3",
                color = ArcGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Please speak the phrase clearly into the microphone:",
                color = scheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // Phrase Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surface)
                    .border(1.5.dp, JarvisCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "“${ENROLLMENT_PROMPTS[currentStep]}”",
                    color = scheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(1f))

            // Central Glowing Reactor / Mic Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(190.dp)
                    .scale(reactorScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.35f),
                                glowColor.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
                    .clickable(enabled = !isRecording) {
                        isRecording = true
                        statusMessage = "Listening... speak now."
                        scope.launch {
                            val audioSample = audioRecorder.recordSample(durationMs = 2400) { progress, rms ->
                                recordingProgress = progress
                                micRmsLevel = rms
                            }

                            if (audioSample.isNotEmpty()) {
                                recordedSamples.add(audioSample)
                                speechManager.playActivationTone()
                                currentStep++
                                recordingProgress = 0f
                                micRmsLevel = 0f
                                isRecording = false

                                if (currentStep < 3) {
                                    statusMessage = "Sample captured. Ready for phase ${currentStep + 1}."
                                } else {
                                    // Complete enrollment: compute and store master voiceprint
                                    val masterProfile = VoiceprintMatcher.enrollMasterProfile(recordedSamples)
                                    settingsStore.saveVoiceProfile(masterProfile)
                                    statusMessage = "Voiceprint calibrated and secured."
                                }
                            } else {
                                isRecording = false
                                statusMessage = "Recording failed. Please try again."
                            }
                        }
                    }
            ) {
                // Circular outer ring & progress
                if (isRecording) {
                    CircularProgressIndicator(
                        progress = { recordingProgress },
                        modifier = Modifier.size(150.dp),
                        color = JarvisCyan,
                        strokeWidth = 5.dp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(scheme.surface)
                        .border(2.dp, glowColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (isRecording) "RECORDING" else "TAP TO\nSPEAK",
                            color = glowColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                statusMessage,
                color = if (isRecording) JarvisCyan else scheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(24.dp))
        } else {
            // Enrollment Complete Screen
            Spacer(Modifier.weight(0.5f))

            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(scheme.surface)
                        .border(1.5.dp, ArcGold.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                        .padding(28.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(ArcGold.copy(alpha = 0.15f))
                            .border(2.dp, ArcGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🛡️", fontSize = 32.sp)
                    }

                    Spacer(Modifier.height(18.dp))

                    Text(
                        "Voice Lock Activated",
                        color = scheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Master voiceprint calculated and locked. JARVIS will now wake up and accept commands exclusively from your voice.",
                        color = scheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = onFinished,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Finish & Enable Voice Match", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}
