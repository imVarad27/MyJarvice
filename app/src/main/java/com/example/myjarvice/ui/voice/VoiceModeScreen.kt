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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VoiceModeBackground = Color(0xFF000000)
private val ControlSurface = Color(0xFF1C1C1E)
private val IconTint = Color(0xFFEDEDED)
private val CaptionColor = Color(0xFF9A9A9E)
private val MutedAccent = Color(0xFFFF5A5A)

/**
 * Full-screen hands-free voice mode: a breathing orb with minimal chrome,
 * modelled on the ChatGPT voice screen.
 */
@Composable
fun VoiceModeScreen(
    isListening: Boolean,
    isSpeaking: Boolean,
    micMuted: Boolean,
    micLevel: Float,
    onToggleMute: () -> Unit,
    onClose: () -> Unit,
    onInfo: () -> Unit,
    onShare: () -> Unit,
    onChangeVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) { onClose() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoiceModeBackground)
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
            TopIconButton(onClick = onInfo, contentDescription = "Session info") { drawInfoIcon(it) }
            Spacer(Modifier.width(18.dp))
            TopIconButton(onClick = onShare, contentDescription = "Share transcript") { drawShareIcon(it) }
            Spacer(Modifier.width(18.dp))
            TopIconButton(onClick = onChangeVoice, contentDescription = "Change voice") { drawSlidersIcon(it) }
        }

        // --- ORB ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VoiceOrb(
                    isListening = isListening && !micMuted,
                    isSpeaking = isSpeaking,
                    micLevel = micLevel
                )
                Spacer(Modifier.height(36.dp))
                Text(
                    text = when {
                        micMuted -> "Muted"
                        isSpeaking -> "Speaking…"
                        isListening -> "Listening…"
                        else -> "Tap the mic to speak"
                    },
                    color = if (micMuted) MutedAccent else CaptionColor,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // --- BOTTOM CONTROLS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleControl(
                onClick = onToggleMute,
                contentDescription = if (micMuted) "Unmute microphone" else "Mute microphone",
                background = if (micMuted) MutedAccent.copy(alpha = 0.22f) else ControlSurface
            ) { drawMicIcon(it, muted = micMuted) }

            CircleControl(
                onClick = onClose,
                contentDescription = "Close voice mode",
                background = ControlSurface
            ) { drawCloseIcon(it) }
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
    micLevel: Float
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val breath by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 700 else 2600, easing = FastOutSlowInEasing),
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
        modifier = Modifier.size(300.dp),
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
                        Color(0xFF4FA8FF).copy(alpha = 0.28f),
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
                        Color(0xFFDCEEFF),
                        Color(0xFF7FC4FF),
                        Color(0xFF2E86E0),
                        Color(0xFF0B4DA2)
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
    draw: DrawScope.(Color) -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = contentDescription) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) { draw(IconTint) }
    }
}

@Composable
private fun CircleControl(
    onClick: () -> Unit,
    contentDescription: String,
    background: Color,
    draw: DrawScope.(Color) -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClickLabel = contentDescription) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(26.dp)) { draw(IconTint) }
    }
}

// ==========================================================================
//  Hand-drawn icons (avoids pulling in material-icons-extended)
// ==========================================================================

private fun DrawScope.drawInfoIcon(tint: Color) {
    val stroke = size.minDimension * 0.09f
    val r = size.minDimension / 2f - stroke / 2f
    val c = Offset(size.width / 2f, size.height / 2f)

    drawCircle(color = tint, radius = r, center = c, style = Stroke(width = stroke))
    drawCircle(color = tint, radius = stroke * 0.62f, center = Offset(c.x, c.y - r * 0.46f))
    drawLine(
        color = tint,
        start = Offset(c.x, c.y - r * 0.06f),
        end = Offset(c.x, c.y + r * 0.52f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawShareIcon(tint: Color) {
    val stroke = size.minDimension * 0.09f
    val w = size.width
    val h = size.height

    // Arrow rising out of the tray.
    drawLine(
        color = tint,
        start = Offset(w / 2f, h * 0.08f),
        end = Offset(w / 2f, h * 0.60f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = tint,
        start = Offset(w * 0.28f, h * 0.30f),
        end = Offset(w / 2f, h * 0.08f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = tint,
        start = Offset(w * 0.72f, h * 0.30f),
        end = Offset(w / 2f, h * 0.08f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )

    // Open-topped tray.
    val trayTop = h * 0.52f
    drawLine(color = tint, start = Offset(w * 0.14f, trayTop), end = Offset(w * 0.14f, h * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color = tint, start = Offset(w * 0.86f, trayTop), end = Offset(w * 0.86f, h * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color = tint, start = Offset(w * 0.14f, h * 0.92f), end = Offset(w * 0.86f, h * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.drawSlidersIcon(tint: Color) {
    val stroke = size.minDimension * 0.085f
    val w = size.width
    val h = size.height
    val rows = listOf(h * 0.22f to w * 0.66f, h * 0.5f to w * 0.34f, h * 0.78f to w * 0.58f)

    rows.forEach { (y, knobX) ->
        drawLine(
            color = tint,
            start = Offset(w * 0.06f, y),
            end = Offset(w * 0.94f, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawCircle(color = VoiceModeBackground, radius = stroke * 1.7f, center = Offset(knobX, y))
        drawCircle(color = tint, radius = stroke * 1.25f, center = Offset(knobX, y), style = Stroke(width = stroke * 0.85f))
    }
}

private fun DrawScope.drawMicIcon(tint: Color, muted: Boolean) {
    val color = if (muted) MutedAccent else tint
    val stroke = size.minDimension * 0.09f
    val w = size.width
    val h = size.height
    val capsuleW = w * 0.40f
    val capsuleH = h * 0.52f

    // Capsule head.
    drawRoundRect(
        color = color,
        topLeft = Offset((w - capsuleW) / 2f, h * 0.06f),
        size = Size(capsuleW, capsuleH),
        cornerRadius = CornerRadius(capsuleW / 2f, capsuleW / 2f)
    )

    // Cradle arc.
    val arcInset = w * 0.16f
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(arcInset, h * 0.36f),
        size = Size(w - arcInset * 2f, h * 0.42f),
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )

    // Stem.
    drawLine(
        color = color,
        start = Offset(w / 2f, h * 0.78f),
        end = Offset(w / 2f, h * 0.94f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )

    if (muted) {
        drawLine(
            color = color,
            start = Offset(w * 0.10f, h * 0.06f),
            end = Offset(w * 0.90f, h * 0.96f),
            strokeWidth = stroke * 1.15f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawCloseIcon(tint: Color) {
    val stroke = size.minDimension * 0.11f
    val inset = size.minDimension * 0.16f
    drawLine(
        color = tint,
        start = Offset(inset, inset),
        end = Offset(size.width - inset, size.height - inset),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = tint,
        start = Offset(size.width - inset, inset),
        end = Offset(inset, size.height - inset),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
}
