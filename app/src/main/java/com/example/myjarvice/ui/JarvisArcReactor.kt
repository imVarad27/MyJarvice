package com.example.myjarvice.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myjarvice.theme.ArcGold
import com.example.myjarvice.theme.JarvisCyan

@Composable
fun JarvisArcReactor(
    isListening: Boolean = false,
    isSpeaking: Boolean = false,
    size: Dp = 90.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcReactorTransition")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 500 else if (isListening) 750 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val activeColor = when {
        isSpeaking -> ArcGold
        isListening -> Color(0xFF10B981)
        else -> JarvisCyan
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val minDim = minOf(this.size.width, this.size.height)
            val maxRadius = minDim / 2.15f

            val strokeThin = (minDim * 0.018f).coerceAtLeast(1.0f)
            val strokeMid = (minDim * 0.035f).coerceAtLeast(1.5f)

            // 1. Subtle ambient aura
            drawCircle(
                color = activeColor.copy(alpha = 0.08f * pulseScale),
                radius = maxRadius * 1.05f * pulseScale,
                center = center
            )

            // 2. Outer segmented precision ring
            rotate(outerRotation, pivot = center) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.35f),
                    radius = maxRadius,
                    center = center,
                    style = Stroke(
                        width = strokeThin,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(minDim * 0.14f, minDim * 0.08f), 0f)
                    )
                )
            }

            // 3. Middle fine structural ring
            drawCircle(
                color = activeColor.copy(alpha = 0.20f),
                radius = maxRadius * 0.78f,
                center = center,
                style = Stroke(width = strokeThin)
            )

            // 4. Inner rotating notched core ring
            rotate(innerRotation, pivot = center) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.65f),
                    radius = maxRadius * 0.62f,
                    center = center,
                    style = Stroke(
                        width = strokeMid,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(minDim * 0.05f, minDim * 0.03f, minDim * 0.02f, minDim * 0.03f),
                            0f
                        )
                    )
                )
            }

            // 5. Core energy lens & spark
            drawCircle(
                color = activeColor.copy(alpha = 0.15f * pulseScale),
                radius = maxRadius * 0.42f * pulseScale,
                center = center
            )

            drawCircle(
                color = activeColor.copy(alpha = 0.85f),
                radius = maxRadius * 0.22f * pulseScale,
                center = center
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = maxRadius * 0.10f * pulseScale,
                center = center
            )
        }
    }
}
