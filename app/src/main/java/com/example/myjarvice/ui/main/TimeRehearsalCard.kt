package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.TimeRehearsal
import com.example.myjarvice.data.TimeRehearsalResult
import java.text.DateFormat
import java.util.Date

/** Same-start stress test, not a booking or an AI prediction of how long the user will take. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimeRehearsalCard(result: TimeRehearsalResult, buffer: Int, expanded: Boolean,
    onExpanded: (Boolean) -> Unit, onBuffer: (Int) -> Unit, calculating: Boolean = false) {
    var onlyResilient by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Science, null, tint = colors.primary)
            Text("Time Rehearsal", style = MaterialTheme.typography.titleLarge)
            Text("What if this commitment takes longer? Test your selected ${result.durationMinutes}-minute length before saying yes.",
                style = MaterialTheme.typography.bodyMedium)
            Text("Private calculation · no AI call · nothing booked", style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant)
            OutlinedButton(onClick = { onExpanded(!expanded) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (expanded) "Hide rehearsal" else "Rehearse a commitment")
            }
            if (expanded) {
                Text("Preparation and recovery", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeRehearsal.buffers.forEach { minutes ->
                        FilterChip(selected = buffer == minutes, onClick = { onBuffer(minutes) },
                            enabled = !calculating, label = { Text("$minutes min each side") }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                Text("Stress test: ${result.overrunMinutes} minutes (+50%), with the same start and buffers. This is an assumption you can inspect—not a forecast.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (calculating) {
                    Text("Recalculating rehearsal…", style = MaterialTheme.typography.bodyMedium)
                } else if (!result.available) {
                    Text("Rehearsal unavailable: calendar data is incomplete. Refresh before trying again.", color = colors.error)
                } else {
                    Text("${result.options.size} sampled ${if (result.options.size == 1) "slot" else "slots"} · ${result.resilientCount} also fit the overrun",
                        style = MaterialTheme.typography.titleMedium)
                    if (result.options.isEmpty()) {
                        Text("No sampled slot fits this length and both buffers. Try a shorter length above or less buffer; Jarvis will not move your events.")
                    } else {
                        FilterChip(selected = onlyResilient, onClick = { onlyResilient = !onlyResilient },
                            label = { Text("Only overrun-safe slots") }, modifier = Modifier.heightIn(min = 48.dp))
                        val shown = result.options.filter { !onlyResilient || it.survivesOverrun }.take(3)
                        if (shown.isEmpty()) Text("None of these sampled slots fits the 50% overrun. The original commitment may fit, but has less margin.")
                        shown.forEach { option ->
                            HorizontalDivider()
                            Text(date.format(Date(option.startsAt)), style = MaterialTheme.typography.titleSmall)
                            Text("Commitment ${time.format(Date(option.startsAt))} – ${time.format(Date(option.endsAt))}",
                                style = MaterialTheme.typography.bodyMedium)
                            Text("With buffers ${time.format(Date(option.protectedFrom))} – ${time.format(Date(option.protectedUntil))}",
                                style = MaterialTheme.typography.bodySmall)
                            Text(if (option.survivesOverrun) "Overrun fits · finishes ${time.format(Date(option.overrunEndsAt))} before recovery"
                                else "Overrun does not fit at this start · would need more room", style = MaterialTheme.typography.labelLarge,
                                color = if (option.survivesOverrun) colors.primary else colors.onSurfaceVariant)
                        }
                        Text("Showing up to three earliest matching slots. Samples use one start per free gap, at most three per day; this is not every possible start.",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                Text("9 am–6 pm, local time; visible calendars only. Tasks, travel, hidden calendars and other commitments are not checked. Refresh before relying on this rehearsal.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
