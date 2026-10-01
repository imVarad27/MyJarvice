package com.example.myjarvice.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WritingProfilePanel() {
    val context = LocalContext.current
    val store = remember { WritingProfileStore(context.applicationContext) }
    var saved by remember { mutableStateOf(store.load()) }
    var enabled by rememberSaveable { mutableStateOf(saved.enabled) }
    var tone by rememberSaveable { mutableStateOf(saved.tone) }
    var length by rememberSaveable { mutableStateOf(saved.length) }
    var contractions by rememberSaveable { mutableStateOf(saved.contractions) }
    var emoji by rememberSaveable { mutableStateOf(saved.emoji) }
    var signOff by rememberSaveable { mutableStateOf(saved.signOff) }
    var avoidPhrases by rememberSaveable { mutableStateOf(saved.avoidPhrases) }
    var status by remember { mutableStateOf("") }
    val draft = WritingProfile(enabled, tone, length, contractions, emoji, signOff, avoidPhrases).normalized()

    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sound more like you", style = MaterialTheme.typography.titleMedium)
            Text("Set how your messages should sound. You can change the tone for any individual draft.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WritingOption("Use for new drafts", enabled) { enabled = it }
            Text("Default tone", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DraftTone.entries.forEach { choice ->
                    FilterChip(tone == choice, { tone = choice }, label = { Text(choice.label) })
                }
            }
            Text("Length", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DraftLength.entries.forEach { choice ->
                    FilterChip(length == choice, { length = choice }, label = { Text(choice.label) })
                }
            }
            WritingOption("Use contractions (I'm, let's)", contractions) { contractions = it }
            WritingOption("Allow occasional emoji", emoji) { emoji = it }
            OutlinedTextField(signOff, { signOff = it.take(WritingProfile.MAX_SIGN_OFF) },
                label = { Text("Sign-off (optional)") }, placeholder = { Text("Thanks, Varad") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(avoidPhrases, { avoidPhrases = it.take(WritingProfile.MAX_AVOID) },
                label = { Text("Phrases to avoid (optional)") }, placeholder = { Text("Dear Sir/Madam, kindly do the needful") },
                maxLines = 3, modifier = Modifier.fillMaxWidth())
            Text("Saved on this phone. When used in a draft, these preferences appear in the editable request. PC mode sends that request to your paired PC.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    store.save(draft)
                    saved = draft
                    status = "Writing style saved"
                }, enabled = draft != saved) { Text("Save writing style") }
                TextButton(onClick = {
                    store.reset()
                    saved = WritingProfile()
                    enabled = saved.enabled; tone = saved.tone; length = saved.length
                    contractions = saved.contractions; emoji = saved.emoji
                    signOff = ""; avoidPhrases = ""
                    status = "Writing style reset"
                }) { Text("Reset") }
            }
            if (status.isNotEmpty() && draft == saved) Text(status, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun WritingOption(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}
