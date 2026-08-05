package com.example.myjarvice.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myjarvice.ui.JarvisArcReactor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animated brand splash: the arc-reactor emblem springs in and the wordmark
 * fades up, then [onFinish] advances to the Welcome screen.
 */
@Composable
fun SplashScreen(onFinish: () -> Unit) {
    val scale = remember { Animatable(0.55f) }
    val alpha = remember { Animatable(0f) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(durationMillis = 700)) }
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        delay(1300)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .scale(scale.value)
                    .alpha(alpha.value)
            ) {
                JarvisArcReactor()
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "J A R V I C",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 6.sp,
                modifier = Modifier.alpha(alpha.value)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Just A Rather Very Intelligent Companion",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha.value)
            )
        }
    }
}
