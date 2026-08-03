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
import androidx.compose.ui.unit.dp
import com.example.myjarvice.theme.ArcGold
import com.example.myjarvice.theme.JarvisBlue
import com.example.myjarvice.theme.JarvisCyan

@Composable
fun JarvisArcReactor(
    isListening: Boolean = false,
    isSpeaking: Boolean = false,
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
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 600 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val activeColor = when {
        isSpeaking -> ArcGold
        isListening -> Color(0xFF00FF66)
        else -> JarvisCyan
    }

    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.width / 2.2f

            // Outer Dashed Arc Ring
            rotate(outerRotation, pivot = center) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.4f),
                    radius = maxRadius,
                    center = center,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 15f), 0f)
                    )
                )
            }

            // Middle Glowing Ring
            drawCircle(
                color = JarvisBlue.copy(alpha = 0.25f),
                radius = maxRadius * 0.8f,
                center = center,
                style = Stroke(width = 8.dp.toPx())
            )

            // Inner Rotating Gear Ring
            rotate(innerRotation, pivot = center) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.7f),
                    radius = maxRadius * 0.65f,
                    center = center,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f, 5f, 10f), 0f)
                    )
                )
            }

            // Pulsing Core Glass Circle
            drawCircle(
                color = activeColor.copy(alpha = 0.15f * pulseScale),
                radius = maxRadius * 0.45f * pulseScale,
                center = center
            )

            // Core Solid Center Arc Energy
            drawCircle(
                color = activeColor,
                radius = maxRadius * 0.25f * pulseScale,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = maxRadius * 0.12f * pulseScale,
                center = center
            )
        }
    }
}
