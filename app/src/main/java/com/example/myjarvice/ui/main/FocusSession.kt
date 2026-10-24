package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.LocalTask
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.ceil

internal object FocusClock {
    val durations = listOf(25, 45, 60)

    fun endAt(startedAt: Long, durationMinutes: Int): Long {
        require(durationMinutes in durations) { "Choose one of the available focus lengths." }
        return startedAt + durationMinutes * 60_000L
    }

    fun remainingMillis(endAt: Long, now: Long): Long = (endAt - now).coerceAtLeast(0L)

    fun progress(remainingMillis: Long, durationMinutes: Int): Float {
        val total = durationMinutes * 60_000L
        return ((total - remainingMillis).toFloat() / total).coerceIn(0f, 1f)
    }

    fun label(remainingMillis: Long): String {
        val seconds = ceil(remainingMillis / 1_000.0).toLong()
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FocusSetupSheet(tasks: List<LocalTask>, onStart: (LocalTask, Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        FocusSetupContent(tasks, onStart)
    }
}

@Composable
internal fun FocusSetupContent(tasks: List<LocalTask>, onStart: (LocalTask, Int) -> Unit) {
    var selectedTaskId by rememberSaveable(tasks.firstOrNull()?.id) { mutableStateOf(tasks.firstOrNull()?.id) }
    var duration by rememberSaveable { mutableStateOf(FocusClock.durations.first()) }
    val selectedTask = tasks.firstOrNull { it.id == selectedTaskId } ?: tasks.firstOrNull()
    Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
        Text("Start a focus session", style = MaterialTheme.typography.headlineSmall)
        Text("Choose one task and a calm, on-screen timer. Jarvis won’t mute your phone, block apps, or mark anything complete by itself.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        Text("Task", style = MaterialTheme.typography.titleSmall)
        tasks.forEach { task ->
            Surface(onClick = { selectedTaskId = task.id }, shape = RoundedCornerShape(18.dp),
                color = if (selectedTask?.id == task.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedTask?.id == task.id, onClick = { selectedTaskId = task.id })
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        Text("Length", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusClock.durations.forEach { minutes ->
                FilterChip(selected = duration == minutes, onClick = { duration = minutes }, label = { Text("$minutes min") })
            }
        }
        Button(onClick = { selectedTask?.let { onStart(it, duration) } }, enabled = selectedTask != null,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Start focus")
        }
    }
}

@Composable
internal fun FocusSessionDialog(
    task: LocalTask,
    durationMinutes: Int,
    startedAt: Long,
    onDismiss: () -> Unit,
    onMarkComplete: () -> Unit
) {
    var pausedAt by rememberSaveable(task.id, startedAt) { mutableStateOf<Long?>(null) }
    var pausedRemaining by rememberSaveable(task.id, startedAt) { mutableLongStateOf(0L) }
    var endAt by rememberSaveable(task.id, startedAt) { mutableLongStateOf(FocusClock.endAt(startedAt, durationMinutes)) }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val remaining = if (pausedAt == null) FocusClock.remainingMillis(endAt, currentTime) else pausedRemaining
    val finished = remaining == 0L
    LaunchedEffect(pausedAt, endAt, finished) {
        while (pausedAt == null && !finished) {
            currentTime = System.currentTimeMillis()
            delay(1_000)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (finished) "Focus block finished" else "Stay with one thing") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(FocusClock.label(remaining), style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 18.dp))
                LinearProgressIndicator(progress = { FocusClock.progress(remaining, durationMinutes) },
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                Text(if (finished) "Take a breath, then choose what happens next." else if (pausedAt != null) "Paused. This timer only runs while Jarvis is open." else "Your timer stays on screen while Jarvis is open.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp))
            }
        },
        confirmButton = {
            when {
                finished -> TextButton(onClick = onMarkComplete) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Mark complete", modifier = Modifier.padding(start = 6.dp))
                }
                pausedAt == null -> TextButton(onClick = {
                    pausedRemaining = FocusClock.remainingMillis(endAt, System.currentTimeMillis())
                    pausedAt = System.currentTimeMillis()
                }, modifier = Modifier.semantics { contentDescription = "Pause focus timer" }) {
                    Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Pause", modifier = Modifier.padding(start = 6.dp))
                }
                else -> TextButton(onClick = {
                    endAt = System.currentTimeMillis() + pausedRemaining
                    pausedAt = null
                    currentTime = System.currentTimeMillis()
                }, modifier = Modifier.semantics { contentDescription = "Resume focus timer" }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Resume", modifier = Modifier.padding(start = 6.dp))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (finished) "Keep task open" else "End session") } }
    )
}
