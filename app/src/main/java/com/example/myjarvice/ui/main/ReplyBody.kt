package com.example.myjarvice.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Reusable answer rendering for chat and popup; links/HTML/code remain inert text. */
@Composable
internal fun ReplyBody(text: String, error: Boolean = false, allowCopy: Boolean = true,
    onCopyCode: ((String) -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val blocks = remember(text) { replyBlocks(text) }
    SelectionContainer {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            blocks.forEach { block -> when (block) {
                is ReplyBlock.Prose -> Text(remember(block.text) { formatReplyText(block.text, interpretFences = false) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (error) colors.error else colors.onSurface)
                is ReplyBlock.Code -> Surface(shape = MaterialTheme.shapes.medium,
                    color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(block.language.ifBlank { "Code" }, style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(enabled = allowCopy, onClick = {
                                if (onCopyCode != null) onCopyCode(block.text) else runCatching {
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                        .setPrimaryClip(ClipData.newPlainText("Jarvis code", block.text))
                                }.onSuccess { Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show() }
                                    .onFailure { Toast.makeText(context, "Couldn't copy code", Toast.LENGTH_SHORT).show() }
                            }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp)); Text("Copy code")
                            }
                        }
                        HorizontalDivider(color = colors.outlineVariant)
                        Text(block.text, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = colors.onSurface,
                            // Cap paragraph width: long minified lines must not create an invalid layout.
                            modifier = Modifier.horizontalScroll(rememberScrollState()).widthIn(max = 1600.dp).padding(16.dp))
                    }
                }
            } }
        }
    }
}
