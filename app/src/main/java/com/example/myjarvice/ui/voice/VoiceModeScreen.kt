package com.example.myjarvice.ui.voice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen hands-free voice mode: a breathing orb with minimal chrome,
 * modelled on the ChatGPT voice screen.
 */
@Composable
fun VoiceModeScreen(
    isListening: Boolean,
    isSpeaking: Boolean,
    isThinking: Boolean,
    micMuted: Boolean,
    micLevel: Float,
    onToggleMute: () -> Unit,
    onClose: () -> Unit,
    onInfo: () -> Unit,
    onShare: () -> Unit,
    onChangeVoice: () -> Unit,
    modifier: Modifier = Modifier,
    liveTranscript: String = "",
    recognitionStatus: String = "",
    ownerVerified: Boolean = false
) {
    BackHandler(enabled = true) { onClose() }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- TOP CHROME ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Voice", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                if (ownerVerified) {
                    Text("Owner voice recognized", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                }
            }
            TopIconButton(onClick = onInfo, contentDescription = "Session info", icon = Icons.Rounded.Info)
            Spacer(Modifier.width(4.dp))
            TopIconButton(onClick = onShare, contentDescription = "Share transcript", icon = Icons.Rounded.Share)
            Spacer(Modifier.width(4.dp))
            TopIconButton(onClick = onChangeVoice, contentDescription = "Change voice", icon = Icons.Rounded.Tune)
        }

        // --- ORB ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                VoiceOrb(
                    isListening = isListening && !micMuted,
                    isSpeaking = isSpeaking,
                    isThinking = isThinking,
                    micLevel = micLevel
                )
                Spacer(Modifier.height(36.dp))
                Text(
                    text = when {
                        micMuted -> "Muted"
                        isThinking -> "Thinking…"
                        isSpeaking -> "Speaking…"
                        recognitionStatus.isNotBlank() -> recognitionStatus
                        isListening -> "Listening…"
                        else -> "Tap the mic to speak"
                    },
                    color = if (micMuted) colors.error else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (liveTranscript.isNotBlank() && !isSpeaking) {
                    Spacer(Modifier.height(16.dp))
                    Text(liveTranscript, color = colors.onSurface, fontSize = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                }
            }
        }

        // --- BOTTOM CONTROLS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleControl(
                onClick = onToggleMute,
                contentDescription = if (micMuted) "Unmute microphone" else "Mute microphone",
                background = if (micMuted) colors.errorContainer else colors.surfaceContainerHigh,
                icon = if (micMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                tint = if (micMuted) colors.error else colors.onSurface
            )

            CircleControl(
                onClick = onClose,
                contentDescription = "Close voice mode",
                background = colors.surfaceContainerHigh,
                icon = Icons.Rounded.Close,
                tint = colors.onSurface
            )
        }
    }
}

/**
 * The orb. Breathes continuously, swells with mic amplitude while listening,
 * and pulses faster while JARVICE speaks.
 */
@Composable
private fun VoiceOrb(
    isListening: Boolean,
    isSpeaking: Boolean,
    isThinking: Boolean,
    micLevel: Float
) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "orb")

    // Thinking gets its own tempo — a steady, deliberate pulse that reads as work in
    // progress rather than the quick cadence of speech or the idle breath.
    val breathPeriod = when {
        isThinking -> 1100
        isSpeaking -> 700
        else -> 2600
    }
    val breath by transition.animateFloat(
        initialValue = if (isThinking) 0.92f else 0.96f,
        targetValue = if (isThinking) 1.08f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(breathPeriod, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )

    // SpeechRecognizer reports roughly -2..10 dB; map that onto a gentle swell.
    val normalisedLevel = ((micLevel + 2f) / 12f).coerceIn(0f, 1f)
    val levelSwell by animateFloatAsState(
        targetValue = if (isListening) 1f + normalisedLevel * 0.18f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "levelSwell"
    )

    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(220.dp)
                .scale(breath * levelSwell)
        ) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Outer halo so the orb doesn't sit flat on pure black.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.primary.copy(alpha = 0.28f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.55f
                ),
                radius = radius * 1.55f,
                center = center
            )

            // Body of the sphere: light crown, deep blue base.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        colors.primaryContainer,
                        colors.primary,
                        colors.primary,
                        colors.onPrimaryContainer
                    ),
                    center = Offset(center.x - radius * 0.28f, center.y - radius * 0.38f),
                    radius = radius * 1.55f
                ),
                radius = radius,
                center = center
            )

            // Slow-drifting cloud banding, clipped to the sphere.
            rotate(drift, center) {
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.00f),
                            Color.White.copy(alpha = 0.20f),
                            Color.White.copy(alpha = 0.00f)
                        ),
                        start = Offset(center.x - radius, center.y - radius * 0.4f),
                        end = Offset(center.x + radius, center.y + radius * 0.6f)
                    ),
                    radius = radius,
                    center = center
                )
            }

            // Specular highlight.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.75f), Color.Transparent),
                    center = Offset(center.x - radius * 0.34f, center.y - radius * 0.44f),
                    radius = radius * 0.5f
                ),
                radius = radius,
                center = center
            )
        }
    }
}

// ==========================================================================
//  Chrome
// ==========================================================================

@Composable
private fun TopIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Transparent)) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CircleControl(
    onClick: () -> Unit,
    contentDescription: String,
    background: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(64.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = background, contentColor = tint)) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(28.dp))
    }
}
