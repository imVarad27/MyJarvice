package com.example.myjarvice.ui.settings

import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.myjarvice.data.BackupProtection
import com.example.myjarvice.data.JarvisBackupManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Passwords are remembered only for the current dialog, never saved across recreation. */
@Composable
fun BackupPanel(onRestored: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { JarvisBackupManager(context) }
    val scope = rememberCoroutineScope()
    val supportsProtection = Build.VERSION.SDK_INT >= 26
    var busy by remember { mutableStateOf(false) }
    var exportUri by remember { mutableStateOf<Uri?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var reviewed by remember { mutableStateOf<JarvisBackupManager.PreparedBackup?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    fun dismissReview() { reviewed?.discard(); reviewed = null }
    DisposableEffect(Unit) { onDispose { reviewed?.discard() } }

    fun prepare(uri: Uri, password: CharArray? = null) {
        busy = true
        failure = null
        scope.launch {
            try {
                reviewed = withContext(Dispatchers.IO) { manager.prepareRestore(uri, password) }
                importUri = null
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                failure = if (error is BackupProtection.AuthenticationFailure) error.message
                    else "Cannot open backup. It may be incomplete, damaged, or an unsupported version. Nothing was restored."
            }
            finally { password?.fill('\u0000'); busy = false }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { failure = null; exportUri = uri }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            failure = null
            scope.launch {
                try {
                    val protected = withContext(Dispatchers.IO) { manager.requiresPassphrase(uri) }
                    if (protected) {
                        if (supportsProtection) importUri = uri
                        else failure = "Protected backups require Android 8 or newer."
                    } else reviewed = withContext(Dispatchers.IO) { manager.prepareRestore(uri) }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { failure = "Cannot read this backup file." }
                finally { busy = false }
            }
        }
    }
    BackupCard(busy, failure, supportsProtection,
        onCreate = {
            failure = null
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            exportLauncher.launch("jarvis-backup-$date.jarvisbackup")
        }, onRestore = { importLauncher.launch(arrayOf("*/*")) })

    exportUri?.let { uri ->
        BackupPassphraseDialog(creating = true, supportsProtection = supportsProtection, busy = busy,
            error = failure, onDismiss = { if (!busy) { exportUri = null; failure = null } },
            onSubmit = { password ->
                busy = true
                failure = null
                scope.launch {
                    try {
                        val preview = withContext(Dispatchers.IO) { manager.exportTo(uri, password) }
                        exportUri = null
                        Toast.makeText(context, "Backup created · ${preview.totalItems} items", Toast.LENGTH_LONG).show()
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) {
                        // Provider errors must not echo document paths or input secrets.
                        failure = "Could not create backup. Check storage and the 32 MB data limit; an empty or incomplete file may remain."
                    } finally { password?.fill('\u0000'); busy = false }
                }
            })
    }
    importUri?.let { uri ->
        BackupPassphraseDialog(creating = false, supportsProtection = supportsProtection, busy = busy,
            error = failure, onDismiss = { if (!busy) { importUri = null; failure = null } },
            onSubmit = { password -> prepare(uri, password) })
    }
    reviewed?.let { snapshot ->
        val preview = snapshot.preview
        AlertDialog(onDismissRequest = { if (!busy) dismissReview() },
            title = { Text("Merge this backup?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Created ${java.text.DateFormat.getDateTimeInstance().format(Date(preview.createdAt))}",
                        style = MaterialTheme.typography.labelMedium)
                    Text("Jarvis found ${preview.totalItems} items:")
                    Text("${preview.conversations} conversations · ${preview.memories} memories · ${preview.documents} documents")
                    Text("${preview.tasks} tasks · ${preview.savedItems} saved items · ${preview.mediaFiles} media files")
                    Text("Adds missing items and updates older matching conversations. Does not delete newer data. Preferences and your writing profile may change.")
                    Text("PC credentials, voiceprints, wake state and model files are never restored.", style = MaterialTheme.typography.bodySmall)
                }
            }, confirmButton = {
                TextButton(onClick = merge@{
                    if (busy || reviewed !== snapshot) return@merge
                    // Transfer ownership to the operation before launching; disposal cannot wipe in-use media.
                    reviewed = null
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { manager.restorePrepared(snapshot) }
                            onRestored()
                            Toast.makeText(context, "Backup merged successfully", Toast.LENGTH_LONG).show()
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { failure = "Merge could not finish. Some items may have been added; your existing data was not deleted." }
                        finally { snapshot.discard(); busy = false }
                    }
                }, enabled = !busy) { Text("Merge backup") }
            }, dismissButton = { TextButton(onClick = ::dismissReview, enabled = !busy) { Text("Cancel") } },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn))
    }
}

@Composable
internal fun BackupCard(busy: Boolean, error: String?, supportsProtection: Boolean, onCreate: () -> Unit, onRestore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Text("Backup & restore", style = MaterialTheme.typography.titleMedium)
        }
        Text("Keep your personal Jarvis data under your control", style = MaterialTheme.typography.bodySmall)
        Text("Back up conversations, memories, documents, tasks, saved items and preferences to a file you choose.",
            style = MaterialTheme.typography.bodyMedium)
        Text(if (supportsProtection) "Passphrase protection is recommended. Jarvis never saves your passphrase or uploads the archive."
            else "Protected backups require Android 8 or newer. Unprotected files contain readable personal data.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Never included: PC address or token, voice profile, wake state, Activity log and AI model files.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Preparing your data…", style = MaterialTheme.typography.labelMedium)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        // Stacked controls remain readable at large font sizes and narrow screen widths.
        Button(onClick = onCreate, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Create backup") }
        OutlinedButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Restore backup") }
    }
}

@Composable
internal fun BackupPassphraseDialog(creating: Boolean, supportsProtection: Boolean, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onSubmit: (CharArray?) -> Unit) {
    var protect by remember { mutableStateOf(supportsProtection) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val needsPassword = !creating || protect
    val passwordError = if (needsPassword && password.isNotEmpty()) BackupProtection.passphraseError(password) else null
    val mismatch = creating && protect && confirmation.isNotEmpty() && password != confirmation
    val valid = !needsPassword || (BackupProtection.passphraseError(password) == null && (!creating || password == confirmation))
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (creating) "Protect your backup" else "Unlock backup") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (creating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Passphrase protection", modifier = Modifier.weight(1f))
                        Switch(checked = protect, onCheckedChange = {
                            protect = it; password = ""; confirmation = ""
                        }, enabled = !busy && supportsProtection,
                            modifier = Modifier.semantics { contentDescription = "Passphrase protection" })
                    }
                    if (!supportsProtection) Text("Protection requires Android 8 or newer.")
                }
                if (needsPassword) {
                    Text(if (creating) "Use several unrelated words. You will need this passphrase to restore on any device. A forgotten passphrase cannot be recovered."
                        else "Enter the passphrase used when this backup was created. Nothing is restored until you review and approve the contents.")
                    OutlinedTextField(value = password, onValueChange = { if (it.length <= 128) password = it },
                        label = { Text("Passphrase") }, singleLine = true, enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                        isError = passwordError != null, supportingText = { Text(passwordError ?: "12–128 characters · not saved by Jarvis") },
                        modifier = Modifier.fillMaxWidth())
                    if (creating) OutlinedTextField(value = confirmation, onValueChange = { if (it.length <= 128) confirmation = it },
                        label = { Text("Confirm passphrase") }, singleLine = true, enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                        isError = mismatch, supportingText = { if (mismatch) Text("Passphrases do not match.") },
                        modifier = Modifier.fillMaxWidth())
                } else Text("This file will NOT be encrypted. Anyone with the file can read your personal data. Store it privately.", color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Preparing locally… this can take a while.") }
            }
        }, confirmButton = {
            TextButton(onClick = {
                val secret = if (needsPassword) password.toCharArray() else null
                password = ""; confirmation = ""
                onSubmit(secret)
            }, enabled = valid && !busy) { Text(if (creating) "Create backup" else "Unlock & review") }
        }, dismissButton = {
            TextButton(onClick = { password = ""; confirmation = ""; onDismiss() }, enabled = !busy) { Text("Cancel") }
        }, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn))
}
