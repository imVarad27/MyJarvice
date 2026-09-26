package com.example.myjarvice.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myjarvice.ui.JarvisBrandMark

@Composable
fun WelcomeScreen(onStartChat: () -> Unit, onVoiceMode: () -> Unit, onSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            JarvisBrandMark(Modifier.size(88.dp))
            Text("Meet Jarvis.", style = MaterialTheme.typography.displaySmall)
            Text("A little help.\nA clearer day.", style = MaterialTheme.typography.headlineMedium)
            Text("Talk through an idea, save something for later, or get things done on your phone.",
                style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            Surface(color = colors.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your choice of AI", style = MaterialTheme.typography.titleMedium)
                    Text("Use an imported phone model or connect your PC. Switch models right from the chat.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            Button(onClick = onStartChat, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Start chatting") }
            OutlinedButton(onClick = onVoiceMode, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Start voice conversation") }
            TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Set up Jarvis") }
        }
    }
}
