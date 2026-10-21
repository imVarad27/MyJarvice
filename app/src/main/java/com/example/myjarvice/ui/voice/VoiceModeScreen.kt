package com.example.myjarvice.ui.voice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Quiet, chat-inspired voice surface; UI changes do not imply improved recognition/model quality. */
@Composable
fun VoiceModeScreen(
    isListening: Boolean, isSpeaking: Boolean, isThinking: Boolean, micMuted: Boolean, micLevel: Float,
    onToggleMute: () -> Unit, onClose: () -> Unit, onInfo: () -> Unit, onShare: () -> Unit,
    onChangeVoice: () -> Unit, modifier: Modifier = Modifier, liveTranscript: String = "",
    recognitionStatus: String = "", ownerVerified: Boolean = false,
    onRetryListening: () -> Unit = {}, routeLabel: String = "", onChooseModel: (() -> Unit)? = null
) {
    BackHandler { onClose() }
    val colors = MaterialTheme.colorScheme
    val state = voicePresentation(isListening, isSpeaking, isThinking, micMuted, recognitionStatus)
    var optionsOpen by remember { mutableStateOf(false) }
    var expandedTranscript by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(colors.background).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Jarvis Voice", style = MaterialTheme.typography.titleMedium)
                if (routeLabel.isNotBlank()) {
                    if (onChooseModel != null) TextButton(onClick = onChooseModel, contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.semantics { contentDescription = "Choose voice response mode" }) {
                        Text(routeLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f, fill = false))
                        Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                    } else Text(routeLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
            Box {
                IconButton(onClick = { optionsOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.MoreHoriz, "Voice options")
                }
                DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                    DropdownMenuItem(text = { Text("Session info") }, leadingIcon = { Icon(Icons.Rounded.Info, null) },
                        onClick = { optionsOpen = false; onInfo() })
                    DropdownMenuItem(text = { Text("Change voice") }, leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                        onClick = { optionsOpen = false; onChangeVoice() })
                    DropdownMenuItem(text = { Text("Share transcript") }, leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = { optionsOpen = false; onShare() })
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val indicatorSize = minOf(180.dp, maxWidth * 0.55f, maxHeight * 0.45f).coerceAtLeast(64.dp)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                VoiceIndicator(isListening && !micMuted && !isSpeaking && !isThinking, isSpeaking, isThinking, micLevel, Modifier.size(indicatorSize))
                Text(state.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text(state.detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center)
                if (ownerVerified) Text("Wake voice matched", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                if (liveTranscript.isNotBlank() && !isSpeaking) {
                    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("You said", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                            Text(liveTranscript, style = MaterialTheme.typography.bodyLarge,
                                maxLines = if (expandedTranscript) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { expandedTranscript = !expandedTranscript }) {
                                Text(if (expandedTranscript) "Show less" else "Show full text")
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledTonalIconButton(onClick = { if (state.canRetry) onRetryListening() else onToggleMute() },
                    modifier = Modifier.size(64.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (micMuted) colors.secondaryContainer else colors.surfaceContainerHigh)) {
                    Icon(if (micMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic, state.micDescription)
                }
                Text(state.micLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledTonalIconButton(onClick = onClose, modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.surfaceContainerHigh)) {
                    Icon(Icons.Rounded.Close, "Close voice mode")
                }
                Text("End", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** No decorative infinite animation. Bars respond only to actual reported microphone level. */
@Composable
private fun VoiceIndicator(listening: Boolean, speaking: Boolean, thinking: Boolean, micLevel: Float, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val level by animateFloatAsState(if (listening) normalizedVoiceLevel(micLevel) else 0f, label = "microphone level")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().testTag("voice-indicator")) {
            drawCircle(colors.primaryContainer)
            val factors = listOf(0.35f, 0.65f, 1f, 0.65f, 0.35f)
            if (!thinking) factors.forEachIndexed { index, factor ->
                val height = size.height * (if (listening) 0.12f + level * factor * 0.42f else if (speaking) factor * 0.4f else 0.12f)
                val width = size.width * 0.055f
                drawRoundRect(colors.onPrimaryContainer,
                    topLeft = Offset(size.width * (0.28f + index * 0.095f), (size.height - height) / 2),
                    size = Size(width, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(width, width))
            }
        }
        if (thinking) Icon(Icons.Rounded.MoreHoriz, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(40.dp))
    }
}
