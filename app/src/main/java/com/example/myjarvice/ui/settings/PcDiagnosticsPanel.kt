package com.example.myjarvice.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.ConnectionDiagnostics
import com.example.myjarvice.data.PcCheck
import com.example.myjarvice.data.PcDiagnosticReport
import com.example.myjarvice.ui.JarvisIconBadge
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun PcDiagnosticsPanel(address: String, token: String) {
    val diagnostics = remember { ConnectionDiagnostics() }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableStateOf(0L) }
    var checking by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<PcDiagnosticReport?>(null) }
    fun cancel() { generation++; job?.cancel(); checking = false; report = null }
    LaunchedEffect(address, token) { cancel() }
    PcDiagnosticsCard(checking, report, onCheck = {
        val attempt = ++generation
        job?.cancel()
        report = null
        checking = true
        job = scope.launch {
            try {
                val result = diagnostics.check(address, token)
                if (attempt == generation) report = result
            } finally { if (attempt == generation) checking = false }
        }
    }, onCancel = ::cancel)
}

@Composable
fun PcDiagnosticsCard(checking: Boolean, report: PcDiagnosticReport?, onCheck: () -> Unit, onCancel: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            JarvisIconBadge(Icons.Rounded.Computer)
            Text("PC connection check", style = MaterialTheme.typography.titleMedium)
            Text("Checks the saved address, pairing and model runtime. No prompts or phone content are sent, and no model is started.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (checking) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Checking your PC…", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onCancel) { Text("Cancel check") }
            } else {
                Button(onClick = onCheck) { Text(if (report == null) "Check connection" else "Check again") }
            }
            report?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (it.check == PcCheck.INSTALLED) Icons.Rounded.CheckCircleOutline else Icons.Rounded.Info, null)
                    Text(it.check.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                }
                it.model?.let { model -> Text("Configured model: $model", style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                if (it.check == PcCheck.INSTALLED) Text(when (it.loaded) {
                    true -> "Loaded now · this is not a generation or GPU-performance test."
                    false -> "Installed but not loaded · the first reply may take longer."
                    null -> "Loaded state unknown · no generation test was performed."
                }, style = MaterialTheme.typography.bodySmall)
                Text(it.check.advice, style = MaterialTheme.typography.bodyMedium)
                it.elapsedMs?.let { elapsed -> Text("Check took ${elapsed} ms · snapshot only; check again if anything changes.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("HTTP/WS over Wi-Fi is unencrypted. Use a trusted network or configured HTTPS/WSS. Sharing a Wi-Fi name doesn't guarantee devices can reach each other.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
