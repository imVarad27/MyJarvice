package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.LocalRoutine
import com.example.myjarvice.data.RoutinePlan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineLibrarySheet(
    routines: List<LocalRoutine>,
    onCreate: () -> Unit,
    onEdit: (LocalRoutine) -> Unit,
    onDelete: (LocalRoutine) -> Unit,
    onRun: (LocalRoutine) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        RoutineLibraryContent(routines, onCreate, onEdit, onDelete, onRun)
    }
}

@Composable
internal fun RoutineLibraryContent(
    routines: List<LocalRoutine>,
    onCreate: () -> Unit,
    onEdit: (LocalRoutine) -> Unit,
    onDelete: (LocalRoutine) -> Unit,
    onRun: (LocalRoutine) -> Unit
) {
    Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
        Text("Routines", style = MaterialTheme.typography.headlineSmall)
        Text("Reusable checklists for the way you work. Running one only prepares phone tasks after you review them.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Create routine")
        }
        if (routines.isEmpty()) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAddCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Start with a repeatable moment", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp))
                    Text("For example: a morning reset, before a meeting, or packing for a trip. Jarvis never runs it automatically.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            routines.forEach { routine ->
                RoutineRow(routine, onEdit = { onEdit(routine) }, onDelete = { onDelete(routine) }, onRun = { onRun(routine) })
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            }
        }
    }
}

@Composable
private fun RoutineRow(routine: LocalRoutine, onEdit: () -> Unit, onDelete: () -> Unit, onRun: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(routine.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${routine.steps.size} ${if (routine.steps.size == 1) "step" else "steps"} · ${routine.steps.take(2).joinToString(" · ")}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        IconButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = "Edit routine: ${routine.title}" }) {
            Icon(Icons.Rounded.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = "Delete routine: ${routine.title}" }) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = onRun, modifier = Modifier.heightIn(min = 44.dp)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Run", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
internal fun RoutineEditorDialog(routine: LocalRoutine?, onDismiss: () -> Unit, onSave: (String, List<String>) -> Unit) {
    var routineTitle: String by rememberSaveable(routine?.id) { mutableStateOf<String>(routine?.title.orEmpty()) }
    var routineStepsText: String by rememberSaveable(routine?.id) { mutableStateOf<String>(routine?.steps?.joinToString("\n").orEmpty()) }
    val steps = RoutinePlan.normalizedSteps(routineStepsText.lines())
    val valid = routineTitle.trim().isNotEmpty() && routineTitle.trim().length <= RoutinePlan.MAX_TITLE_LENGTH &&
        steps.isNotEmpty() && steps.size <= RoutinePlan.MAX_STEPS && steps.all { it.length <= RoutinePlan.MAX_STEP_LENGTH }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (routine == null) "Create routine" else "Edit routine") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("One step per line. You’ll always review the steps before they become phone tasks.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = routineTitle, onValueChange = { value: String -> routineTitle = value.take(RoutinePlan.MAX_TITLE_LENGTH) }, label = { Text("Routine name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                Text("${routineTitle.length}/${RoutinePlan.MAX_TITLE_LENGTH}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = routineStepsText, onValueChange = { value: String -> routineStepsText = value.take(4_800) }, label = { Text("Steps") },
                    minLines = 4, maxLines = 9, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("${steps.size}/${RoutinePlan.MAX_STEPS} steps · up to ${RoutinePlan.MAX_STEP_LENGTH} characters each",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(routineTitle, steps) }, enabled = valid) { Text("Save routine") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun RoutineRunDialog(routine: LocalRoutine, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${routine.steps.size} tasks?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${routine.title} will add these tasks to your private phone list. Nothing is sent, opened or completed automatically.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                routine.steps.forEachIndexed { index, step ->
                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = if (index == 0) 12.dp else 6.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Add tasks") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
