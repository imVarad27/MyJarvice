package com.example.myjarvice.ui.files

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myjarvice.data.FileTransferManager
import com.example.myjarvice.data.PcFileEntry
import com.example.myjarvice.theme.ArcGold
import com.example.myjarvice.ui.icons.IconActivity
import com.example.myjarvice.ui.icons.IconDocument
import com.example.myjarvice.ui.icons.IconPlus
import kotlinx.coroutines.launch

@Composable
fun PcExplorerDialog(
    serverIp: String,
    token: String,
    onDismiss: () -> Unit,
    onAskJarvisAboutFile: (String, String) -> Unit = { _, _ -> }
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentPath by remember { mutableStateOf("") }
    var parentPath by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<PcFileEntry>>(emptyList()) }
    var selectedPreset by remember { mutableStateOf("projects") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedFile by remember { mutableStateOf<PcFileEntry?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }

    fun loadDirectory(path: String? = null, preset: String? = null) {
        isLoading = true
        errorMessage = null
        selectedFile = null
        scope.launch {
            val res = FileTransferManager.browsePcDirectory(serverIp, token, path, preset)
            isLoading = false
            res.onSuccess { data ->
                currentPath = data.currentPath
                parentPath = data.parentPath
                entries = data.entries
            }.onFailure { err ->
                errorMessage = err.message ?: "Failed to load PC directory"
            }
        }
    }

    LaunchedEffect(Unit) {
        loadDirectory(preset = selectedPreset)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.outline.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📁", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Host PC File Explorer",
                                color = scheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Browse & download files from your computer",
                                color = scheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceVariant)
                    ) {
                        Text("✕", color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Presets Shortcuts (Projects, Downloads, Documents, Desktop)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val presets = listOf(
                        "projects" to "💻 Workspace",
                        "downloads" to "📥 Downloads",
                        "documents" to "📄 Documents",
                        "desktop" to "🖥️ Desktop"
                    )
                    items(presets) { (key, label) ->
                        val isSelected = selectedPreset == key
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) scheme.primary.copy(alpha = 0.18f) else scheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    if (isSelected) scheme.primary else scheme.outline.copy(alpha = 0.25f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedPreset = key
                                    loadDirectory(preset = key)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                label,
                                color = if (isSelected) scheme.primary else scheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Path Navigation Bar (with Up button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (!parentPath.isNullOrBlank()) {
                                loadDirectory(path = parentPath)
                            }
                        },
                        enabled = !parentPath.isNullOrBlank(),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text(
                            "⬆",
                            color = if (!parentPath.isNullOrBlank()) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.4f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Text(
                        currentPath.ifBlank { "Root" },
                        color = scheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Content Listing Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = scheme.primary, modifier = Modifier.size(36.dp))
                        }
                    } else if (errorMessage != null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Failed to connect to PC", color = Color(0xFFEF4444), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(errorMessage ?: "", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { loadDirectory(currentPath) },
                                    colors = ButtonDefaults.buttonColors(containerColor = scheme.surfaceVariant)
                                ) {
                                    Text("Retry", color = scheme.primary)
                                }
                            }
                        }
                    } else if (entries.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Folder is empty", color = scheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(entries) { item ->
                                val isSelected = selectedFile?.path == item.path
                                val icon = when {
                                    item.isDir -> "📁"
                                    item.ext in listOf(".pdf") -> "📄"
                                    item.ext in listOf(".png", ".jpg", ".jpeg", ".webp", ".gif") -> "🖼️"
                                    item.ext in listOf(".kt", ".py", ".java", ".ts", ".js", ".json", ".xml", ".sql", ".html") -> "💻"
                                    item.ext in listOf(".zip", ".rar", ".exe", ".apk") -> "📦"
                                    else -> "📄"
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) scheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable {
                                            if (item.isDir) {
                                                loadDirectory(path = item.path)
                                            } else {
                                                selectedFile = if (selectedFile?.path == item.path) null else item
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(icon, fontSize = 16.sp)
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                item.name,
                                                color = scheme.onSurface,
                                                fontSize = 13.sp,
                                                fontWeight = if (item.isDir) FontWeight.Medium else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!item.isDir) {
                                                Text(
                                                    "${formatFileSize(item.sizeBytes)} • ${item.mtime}",
                                                    color = scheme.onSurfaceVariant,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }

                                    if (item.isDir) {
                                        Text("›", color = scheme.onSurfaceVariant, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Bar for Selected File (Download, Open on PC, Ask JARVIS)
                if (selectedFile != null) {
                    val file = selectedFile!!
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = scheme.outline.copy(alpha = 0.25f))
                    Spacer(Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Text(
                            file.name,
                            color = scheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // ⬇️ Download to Phone Button
                            Button(
                                onClick = {
                                    isDownloading = true
                                    scope.launch {
                                        val res = FileTransferManager.downloadFileFromPc(
                                            context = context,
                                            serverIp = serverIp,
                                            token = token,
                                            remotePath = file.path,
                                            onProgress = { downloadProgress = it }
                                        )
                                        isDownloading = false
                                        res.onSuccess { savedFile ->
                                            Toast.makeText(context, "Saved to Downloads: ${savedFile.name}", Toast.LENGTH_LONG).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Download failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isDownloading,
                                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (isDownloading) "Saving..." else "⬇ Download",
                                    color = scheme.onPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // 🖥️ Open on PC Button
                            Button(
                                onClick = {
                                    scope.launch {
                                        val res = FileTransferManager.openFileOnPc(serverIp, token, file.path)
                                        res.onSuccess {
                                            Toast.makeText(context, "Opened on PC", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Could not open: ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = scheme.surface),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, scheme.outline.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            ) {
                                Text("🖥️ Open on PC", color = scheme.onSurface, fontSize = 11.sp)
                            }

                            // 💬 Ask JARVIS Button
                            Button(
                                onClick = {
                                    onAskJarvisAboutFile(file.name, file.path)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = scheme.surface),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, scheme.outline.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            ) {
                                Text("💬 Ask JARVIS", color = scheme.primary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
