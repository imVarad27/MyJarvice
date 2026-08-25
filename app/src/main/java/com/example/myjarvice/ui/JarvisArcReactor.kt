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
import com.example.myjarvice.theme.JarvisBlue
import com.example.myjarvice.theme.JarvisCyan

@Composable
fun JarvisArcReactor(
    isListening: Boolean = false,
    isSpeaking: Boolean = false,
    size: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcReactorTransition")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 550 else if (isListening) 800 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val activeColor = when {
        isSpeaking -> ArcGold
        isListening -> Color(0xFF00FF88)
        else -> JarvisCyan
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val minDim = minOf(this.size.width, this.size.height)
            val maxRadius = minDim / 2.1f

            val strokeOuter = (minDim * 0.025f).coerceAtLeast(1.5f)
            val strokeMid = (minDim * 0.045f).coerceAtLeast(2f)
            val strokeInner = (minDim * 0.02f).coerceAtLeast(1f)

            // Outer Dashed Arc Ring
            rotate(outerRotation, pivot = center) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.45f),
                    radius = maxRadius,
                    center = center,
                    style = Stroke(
                        width = strokeOuter,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(minDim * 0.12f, minDim * 0.06f), 0f)
                    )
                )
            }

            // Middle Glowing Ring
            drawCircle(
                color = JarvisBlue.copy(alpha = 0.25f),
                radius = maxRadius * 0.8f,
                center = center,
                style = Stroke(width = strokeMid)
            )

            // Inner Rotating Gear Ring
            rotate(innerRotation, pivot = center) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.75f),
                    radius = maxRadius * 0.65f,
                    center = center,
                    style = Stroke(
                        width = strokeInner,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(minDim * 0.06f, minDim * 0.04f, minDim * 0.02f, minDim * 0.04f),
                            0f
                        )
                    )
                )
            }

            // Pulsing Core Glass Aura
            drawCircle(
                color = activeColor.copy(alpha = 0.18f * pulseScale),
                radius = maxRadius * 0.46f * pulseScale,
                center = center
            )

            // Core Solid Center Arc Energy
            drawCircle(
                color = activeColor.copy(alpha = 0.85f),
                radius = maxRadius * 0.26f * pulseScale,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = maxRadius * 0.12f * pulseScale,
                center = center
            )
        }
    }
}
