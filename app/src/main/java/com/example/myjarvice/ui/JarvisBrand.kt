package com.example.myjarvice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.myjarvice.R

/** Shared vector identity for onboarding, chat, popup, and the launcher. */
@Composable
fun JarvisBrandMark(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(64.dp).clip(RoundedCornerShape(28))
        .background(Brush.linearGradient(listOf(colors.primary, colors.secondary)))) {
        Icon(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
            tint = colors.onPrimary, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun JarvisPageHeader(title: String, subtitle: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
