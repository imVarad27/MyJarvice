package com.example.myjarvice.ui.main

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myjarvice.data.ConnectionStatus
import com.example.myjarvice.data.JarviceMessage
import com.example.myjarvice.theme.ArcGold
import com.example.myjarvice.theme.JarvisBlue
import com.example.myjarvice.theme.JarvisCyan
import com.example.myjarvice.theme.JarvisDarkBackground
import com.example.myjarvice.theme.JarvisSurfaceBorder
import com.example.myjarvice.theme.JarvisSurfaceDark
import com.example.myjarvice.theme.TextPrimary
import com.example.myjarvice.theme.TextSecondary
import com.example.myjarvice.ui.JarvisArcReactor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: MainScreenViewModel = viewModel {
        MainScreenViewModel(context.applicationContext as Application)
    }

    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val serverIp by viewModel.serverIp.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    var showIpDialog by remember { mutableStateOf(false) }

    if (showIpDialog) {
        var tempIp by remember { mutableStateOf(serverIp) }
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text("Server Config", color = JarvisCyan) },
            text = {
                Column {
                    Text("Enter Host PC Local IP address (e.g., 192.168.1.100 or 10.0.2.2 for emulator):", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempIp,
                        onValueChange = { tempIp = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisSurfaceBorder,
                            focusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val targetIp = tempIp.trim()
                    viewModel.updateServerIp(targetIp)
                    android.widget.Toast.makeText(context, "Connecting to $targetIp...", android.widget.Toast.LENGTH_SHORT).show()
                    showIpDialog = false
                }) {
                    Text("Connect", color = JarvisCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = JarvisSurfaceDark
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JarvisDarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- TOP HUD HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(JarvisSurfaceDark)
                    .border(1.dp, JarvisSurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("MYJARVICE HUD v1.0", color = JarvisCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    Text("HOST: $serverIp:8000", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (statusColor, statusText) = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> Color(0xFF00FF66) to "ONLINE"
                        ConnectionStatus.CONNECTING -> ArcGold to "CONNECTING"
                        ConnectionStatus.DISCONNECTED -> Color.Gray to "OFFLINE"
                        ConnectionStatus.ERROR -> Color.Red to "ERROR"
                    }

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { showIpDialog = true }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- CENTER ARC REACTOR HUD ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                JarvisArcReactor(isListening = isListening, isSpeaking = isSpeaking)
            }

            Text(
                text = when {
                    isListening -> ">>> LISTENING TO USER VOICE <<<"
                    isSpeaking -> ">>> JARVICE SPEAKING <<<"
                    connectionStatus == ConnectionStatus.CONNECTED -> "GEMMA 9B ENGINE READY"
                    else -> "SERVER STANDBY (CLICK STATUS TO SET IP)"
                },
                color = if (isListening) Color(0xFF00FF66) else if (isSpeaking) ArcGold else JarvisCyan,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(12.dp))

            // --- QUICK ACTION CHIPS ---
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val quickPrompts = listOf(
                    "Jarvis, check status",
                    "Jarvis, turn on lab lights",
                    "Jarvis, what is my schedule?",
                    "Jarvis, weather report"
                )
                items(quickPrompts) { prompt ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(JarvisSurfaceDark)
                            .border(1.dp, JarvisSurfaceBorder, RoundedCornerShape(16.dp))
                            .clickable { viewModel.sendQuery(prompt) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(prompt, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- CHAT LOGS FEED ---
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(JarvisSurfaceDark)
                    .border(1.dp, JarvisSurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chatHistory) { msg ->
                    ChatBubble(msg)
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- BOTTOM VOICE & INPUT BAR ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Command Jarvice...", color = TextSecondary, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisSurfaceBorder,
                        focusedTextColor = TextPrimary,
                        cursorColor = JarvisCyan
                    ),
                    shape = RoundedCornerShape(24.dp)
                )

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            viewModel.sendQuery(textInput)
                            textInput = ""
                        } else {
                            viewModel.toggleVoiceInput()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isListening) Color(0xFF00FF66) else JarvisCyan),
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Text(if (isListening) "🎙️" else "⚡", fontSize = 18.sp, color = JarvisDarkBackground)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: JarviceMessage) {
    val isUser = msg.sender == "USER"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) JarvisBlue.copy(alpha = 0.3f) else JarvisDarkBackground
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isUser) JarvisBlue else JarvisCyan
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    msg.sender,
                    color = if (isUser) JarvisBlue else JarvisCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    msg.text,
                    color = TextPrimary,
                    fontSize = 13.sp
                )
            }
        }
    }
}
