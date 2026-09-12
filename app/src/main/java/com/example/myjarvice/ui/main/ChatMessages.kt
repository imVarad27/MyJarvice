package com.example.myjarvice.ui.main

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myjarvice.data.JarvisMessage
import com.example.myjarvice.ui.JarvisArcReactor
import com.example.myjarvice.ui.icons.*
import kotlinx.coroutines.delay

@Composable
internal fun ChatFeed(
    chatHistory: List<JarvisMessage>,
    isThinking: Boolean,
    onCopy: (String) -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(chatHistory.size, isThinking) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1 + if (isThinking) 1 else 0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(chatHistory) { msg ->
            if (msg.sender == "USER") {
                UserMessageBubble(msg = msg)
            } else {
                JarvisMessageBubble(
                    msg = msg,
                    onCopy = { onCopy(msg.text) },
                    onSpeak = { onSpeak(msg.text) }
                )
            }
        }

        if (isThinking) {
            item {
                ThinkingIndicator()
            }
        }
    }
}

@Composable
private fun UserMessageBubble(msg: JarvisMessage) {
    val scheme = MaterialTheme.colorScheme
    var expandedPhoto by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            val bitmap = remember(msg.image) {
                msg.image?.let { payload -> runCatching {
                    val bytes = Base64.decode(payload.substringAfter("base64,"), Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull() }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Photo sent to Jarvis",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Expand attached photo") { expandedPhoto = true }
                )
                if (expandedPhoto) FullscreenImageDialog(bitmap, onDismiss = { expandedPhoto = false }, title = "Attached photo")
                Spacer(Modifier.height(8.dp))
            }
            Text(
                msg.text,
                color = scheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun JarvisMessageBubble(
    msg: JarvisMessage,
    onCopy: () -> Unit,
    onSpeak: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showFullscreenImage by remember { mutableStateOf(false) }
    var showSources by remember(msg.text) { mutableStateOf(false) }
    val answer = msg.text.substringBefore("\n\nRetrieved sources:\n")
    val sources = msg.text.substringAfter("\n\nRetrieved sources:\n", "")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        // Mini Avatar
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            JarvisArcReactor(size = 18.dp)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "JARVIS",
                    color = scheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    when {
                        msg.type == "ERROR" -> "Couldn't finish"
                        msg.sender.contains("On-device", true) -> "On your phone"
                        msg.sender.contains("Local tool", true) -> "Local tool"
                        else -> "PC / server"
                    },
                    color = scheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            // Body text
            SelectionContainer {
                Text(
                    answer,
                    color = if (msg.type == "ERROR") scheme.error else scheme.onSurface,
                    fontSize = 16.sp,
                    lineHeight = 25.sp
                )
            }
            if (sources.isNotBlank()) {
                TextButton(onClick = { showSources = !showSources }) {
                    Text(if (showSources) "Hide sources" else "View sources")
                }
                if (showSources) SelectionContainer {
                    Text(sources, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }

            // Desktop Screenshot / Image Rendering
            if (!msg.image.isNullOrBlank()) {
                val bitmap: Bitmap? = remember(msg.image) {
                    try {
                        val rawBase64 = msg.image.substringAfter("base64,")
                        val bytes = Base64.decode(rawBase64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (bitmap != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceVariant)
                            .border(1.dp, scheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable { showFullscreenImage = true }
                            .padding(6.dp)
                    ) {
                        Column {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Host PC Screenshot",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "PC screenshot · Tap to expand",
                                    color = scheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (showFullscreenImage) {
                        FullscreenImageDialog(
                            bitmap = bitmap,
                            onDismiss = { showFullscreenImage = false }
                        )
                    }
                }
            }

            // Live Web Sources Row (Clickable citation chips)
            if (msg.sources.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, scheme.outline.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Sources",
                            color = scheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(msg.sources) { src ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(scheme.surface)
                                    .border(1.dp, scheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (src.url.isNotBlank()) {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(src.url))
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not open source link", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    src.domain.ifBlank { "source" },
                                    color = scheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("↗", color = scheme.primary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action Toolbar (Copy, Speak, Timestamp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onCopy)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconCopy(tint = scheme.onSurfaceVariant, size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", color = scheme.onSurfaceVariant, fontSize = 11.sp)
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onSpeak)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconSpeaker(tint = scheme.onSurfaceVariant, size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Listen", color = scheme.onSurfaceVariant, fontSize = 11.sp)
                }

                val time = formatTimestamp(msg.timestamp)
                if (time.isNotBlank()) {
                    Text(
                        time,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Fullscreen Interactive Image Dialog
 */
@Composable
private fun FullscreenImageDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    title: String = "PC screenshot"
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )

            // Top Bar with Close Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Close image" }
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    val scheme = MaterialTheme.colorScheme
    var elapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); elapsedSeconds++ }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            JarvisArcReactor(size = 18.dp, isSpeaking = true)
        }

        Spacer(Modifier.width(12.dp))

        val transition = rememberInfiniteTransition(label = "thinking")
        val alpha by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
            label = "thinkingAlpha"
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when {
                    elapsedSeconds >= 20 -> "Taking a little longer… ${elapsedSeconds}s"
                    elapsedSeconds >= 8 -> "Still working on it… ${elapsedSeconds}s"
                    else -> "Thinking it through…"
                },
                color = scheme.primary.copy(alpha = alpha),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatTimestamp(ts: String): String {
    if (ts.length < 16 || !ts.contains("T")) return ""
    val timePart = ts.substringAfter("T")
    return if (timePart.length >= 5) timePart.substring(0, 5) else ""
}
