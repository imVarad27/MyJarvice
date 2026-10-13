package com.example.myjarvice.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myjarvice.BuildConfig
import com.example.myjarvice.data.AppRelease
import com.example.myjarvice.data.AppVersionInfo
import com.example.myjarvice.data.JarvisReleases

internal val installedAppVersion: AppVersionInfo
    get() = AppVersionInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.DEBUG)

@Composable
internal fun ReleaseNotesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = installedAppVersion
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ReleaseNotesContent(version, JarvisReleases.latest, onDismiss, onCopyVersion = {
            runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Jarvis version", version.summary))
            }.onSuccess { Toast.makeText(context, "App version copied", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Couldn't copy app version", Toast.LENGTH_SHORT).show() }
        })
    }
}

/** Offline, read-only catalog. No permission request, model inference, update download or telemetry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReleaseNotesContent(version: AppVersionInfo, release: AppRelease,
    onDismiss: () -> Unit, onCopyVersion: () -> Unit) {
    val notesMatch = version.versionName == release.version
    Scaffold(topBar = {
        TopAppBar(title = { Text("What's new", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close release notes")
            } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.NewReleases, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Jarvis ${version.versionName}", style = MaterialTheme.typography.headlineMedium)
                        Text("Build ${version.versionCode} · ${version.buildLabel}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onCopyVersion, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp)); Text("Copy app version")
                        }
                        Text("Copies only the app version and build type—not your phone, chats, model or connection details.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (notesMatch) {
                item {
                    Text("In this update", style = MaterialTheme.typography.titleLarge)
                    Text("Release notes · ${release.date}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(release.highlights) { highlight ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(highlight.title, style = MaterialTheme.typography.titleMedium)
                            Text(highlight.description, style = MaterialTheme.typography.bodyMedium)
                            Text(highlight.location, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else item {
                Text("Release notes aren't available for this build. The installed version above is still accurate.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Text("Version numbers identify app updates, not AI intelligence. Responses depend on your model. Calendar, contacts, backups and sharing have device-specific limits; voice accuracy needs real acoustic testing.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
