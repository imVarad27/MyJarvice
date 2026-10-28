package com.example.myjarvice.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.ConnectionDiagnostics
import com.example.myjarvice.data.PairingProfile
import com.example.myjarvice.data.PcCheck
import com.example.myjarvice.data.PcDiagnosticReport
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingImportDialog(
    currentHost: String = "",
    onDismiss: () -> Unit,
    onPairingApplied: (host: String, token: String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val diagnostics = remember { ConnectionDiagnostics() }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    var rawInput by rememberSaveable { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var testReport by remember { mutableStateOf<PcDiagnosticReport?>(null) }

    val parsedProfile = remember(rawInput) { PairingProfile.parse(rawInput) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = scheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("PC Setup Code")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Paste your PC pairing code or link from the workstation terminal (run 'python server/pair_helper.py' on your PC).",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = rawInput,
                    onValueChange = {
                        rawInput = it
                        testReport = null
                    },
                    label = { Text("Pairing code or link") },
                    placeholder = { Text("jarvis-pair://v1?host=... or JSON") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboardManager.getText()?.text?.let { clipText ->
                                rawInput = clipText
                                testReport = null
                            }
                        }) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste from clipboard")
                        }
                    }
                )

                parsedProfile?.let { profile ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Detected Configuration", style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                            Text("Host: ${profile.host}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Token: ${profile.maskedToken}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }

                    if (testReport == null) {
                        OutlinedButton(
                            onClick = {
                                testing = true
                                testReport = null
                                scope.launch {
                                    try {
                                        val report = diagnostics.check(profile.host, profile.token)
                                        testReport = report
                                    } catch (_: Exception) {
                                        testReport = PcDiagnosticReport(PcCheck.UNREACHABLE)
                                    } finally {
                                        testing = false
                                    }
                                }
                            },
                            enabled = !testing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (testing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Testing host...")
                            } else {
                                Text("Test Connection")
                            }
                        }
                    }

                    testReport?.let { report ->
                        val isOk = report.check == PcCheck.INSTALLED
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isOk) scheme.primaryContainer else scheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isOk) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = if (isOk) scheme.onPrimaryContainer else scheme.onErrorContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        report.check.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isOk) scheme.onPrimaryContainer else scheme.onErrorContainer
                                    )
                                    Text(
                                        report.check.advice,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isOk) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onErrorContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    parsedProfile?.let { profile ->
                        onPairingApplied(profile.host, profile.token)
                        Toast.makeText(context, "PC connection updated", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                enabled = parsedProfile != null
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
