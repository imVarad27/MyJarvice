package com.example.myjarvice

import android.app.Application
import android.app.KeyguardManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myjarvice.data.*
import com.example.myjarvice.theme.MyJarvisTheme
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Private comparison screen; never starts inference or changes the model from an external intent. */
class LocalModelBenchmarkActivity : ComponentActivity() {
    private val comparison: LocalModelComparisonViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if ((getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) { finish(); return }
        setContent {
            val settings = remember { SettingsStore(applicationContext) }
            MyJarvisTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor, assistantStyle = settings.assistantStyle) {
                LocalModelComparisonScreen(comparison, onBack = { comparison.stop(); finish() })
            }
        }
    }
    override fun onStop() { if (!isChangingConfigurations) comparison.stop(); super.onStop() }
}

data class LocalComparisonState(val models: List<File> = emptyList(), val selected: String = "",
    val active: String = "", val fallback: String = "", val reports: Map<String, BenchmarkReport> = emptyMap(),
    val busy: Boolean = false, val stopping: Boolean = false, val importing: Boolean = false, val message: String = "")

class LocalModelComparisonViewModel(application: Application) : AndroidViewModel(application) {
    private val library = LocalModelLibrary(application)
    private val settings = SettingsStore(application)
    private val mutableState = MutableStateFlow(LocalComparisonState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    init { refresh() }

    private fun refresh() {
        val models = library.models()
        mutableState.value = mutableState.value.copy(models = models,
            selected = mutableState.value.selected.takeIf { path -> models.any { it.path == path } } ?: models.firstOrNull()?.path.orEmpty(),
            active = library.activeModel()?.path.orEmpty(), fallback = library.fallbackModel()?.path.orEmpty(), reports = models.mapNotNull { file -> library.report(file)?.let { file.path to it } }.toMap())
    }
    fun select(model: File) { if (!state.value.busy && !state.value.importing) mutableState.value = state.value.copy(selected = model.path, message = "") }
    fun import(uri: Uri) {
        if (state.value.busy || state.value.importing) return
        job = viewModelScope.launch {
            mutableState.value = state.value.copy(importing = true, message = "Adding candidate. Current model will not be replaced.")
            try {
                val file = withContext(Dispatchers.IO) { library.importCandidate(uri) }
                refresh()
                mutableState.value = state.value.copy(selected = file.path, message = "Candidate added. Run the current model first, then this candidate.")
            } catch (cancelled: CancellationException) {
                mutableState.value = state.value.copy(message = "Import stopped. Current model unchanged.")
                throw cancelled
            }
            catch (error: Exception) { mutableState.value = state.value.copy(message = "Import failed: ${error.message}") }
            finally { mutableState.value = state.value.copy(importing = false) }
        }
    }
    fun run() {
        if (state.value.busy || state.value.importing || LocalBenchmarkRuntime.active.value) return
        val model = state.value.models.firstOrNull { it.path == state.value.selected } ?: return
        mutableState.value = state.value.copy(busy = true, stopping = false, message = "Checking available RAM…")
        job = viewModelScope.launch {
            try {
                LocalBenchmarkRunner(getApplication()).run(model) { report ->
                    withContext(Dispatchers.Main) {
                        mutableState.value = state.value.copy(reports = state.value.reports + (model.path to report), message = report.note)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.value = state.value.copy(message = "Comparison failed: ${error.message}") }
            finally {
                refresh()
                mutableState.value = state.value.copy(busy = false, stopping = false,
                    message = if (state.value.stopping) library.report(model)?.note ?: "Comparison stopped." else state.value.message)
            }
        }
    }
    fun stop() {
        if (state.value.busy) mutableState.value = state.value.copy(stopping = true, message = "Stopping after the current test returns…")
        job?.cancel()
    }
    fun activate(model: File) {
        if (state.value.busy || state.value.importing) return
        val fallback = library.fallbackModel()
        // Re-read persisted reports so a modified file cannot inherit an older passing result.
        if (model != fallback && !LocalModelBenchmark.mayPromote(fallback?.let(library::report), library.report(model))) return
        settings.onDeviceModelPath = model.path
        mutableState.value = state.value.copy(active = model.path, message = "Active local model updated. The fallback file is still available.")
    }
}

@Composable
private fun LocalModelComparisonScreen(viewModel: LocalModelComparisonViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::import) }
    val fallback = state.models.firstOrNull { it.path == state.fallback }
    val selected = state.models.firstOrNull { it.path == state.selected }
    val report = state.reports[state.selected]
    val baseline = fallback?.let { state.reports[it.path] }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack) { Text("Back") }
                TextButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !state.busy && !state.importing) { Text("Add model") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Local model lab", style = MaterialTheme.typography.headlineMedium)
                Text("Compare accuracy, response time and observed memory on this phone. Tests use synthetic prompts only—no chats, saved facts or tools.", style = MaterialTheme.typography.bodyMedium)
                Text("Active: ${File(state.active).name.ifBlank { "No model" }}", color = MaterialTheme.colorScheme.primary)
                if (state.models.isEmpty()) Text("Add a compatible .litertlm model to begin.")
                state.models.forEach { model ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.select(model) }, enabled = !state.busy && !state.importing) {
                        Row(Modifier.padding(12.dp)) {
                            RadioButton(selected = model.path == state.selected, onClick = { viewModel.select(model) }, enabled = !state.busy && !state.importing)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(model.name, style = MaterialTheme.typography.titleSmall)
                                Text("${model.length() / (1024 * 1024)} MB · CPU" + if (model == fallback) " · fallback" else " · candidate", style = MaterialTheme.typography.bodySmall)
                                state.reports[model.path]?.let { result ->
                                    Text(if (result.complete) "${result.passed}/${result.tested} automatic checks · ${"%.1f".format(result.averageMs / 1000)} s average" else "Incomplete check · ${result.cases.size}/7 tests", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                if (state.busy || state.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.message.isNotBlank() && (state.busy || state.importing || state.message != report?.note))
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                report?.let { result ->
                    Text("${result.cases.size}/7 tests recorded", style = MaterialTheme.typography.titleMedium)
                    Text("Available before test: ${LocalBenchmarkRunner.gib(result.availableBeforeBytes)} GB\nLargest observed app PSS: ${result.maxObservedPssKb / 1024} MB (sampled, not peak memory).\nAverage includes startup in the first test.", style = MaterialTheme.typography.bodySmall)
                    if (!state.busy) Text(result.note, style = MaterialTheme.typography.bodySmall)
                    result.cases.forEach { case ->
                        var expanded by remember(case) { mutableStateOf(false) }
                        OutlinedCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${when { case.error != null -> "Error"; case.passed == null -> "Review"; case.passed -> "Pass"; else -> "Fail" }} · ${case.label}", style = MaterialTheme.typography.titleSmall)
                                Text("${"%.1f".format(case.elapsedMs / 1000.0)} seconds · tap to ${if (expanded) "collapse" else "review"}", style = MaterialTheme.typography.bodySmall)
                                if (expanded) {
                                    Text(case.error ?: case.answer.ifBlank { "No text returned" })
                                    LocalModelBenchmark.questions.firstOrNull { it.id == case.id }?.expected?.let { Text("Expected: $it", style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                }
                Text("A candidate can be activated only after completing this suite with a higher automatic score and no more than 1.5× the fallback's average time. Review its tone yourself. This small check does not establish frontier-model ability.", style = MaterialTheme.typography.bodySmall)
                if (selected != null && selected != fallback) {
                    Button(onClick = { viewModel.activate(selected) }, enabled = !state.busy && !state.importing && LocalModelBenchmark.mayPromote(baseline, report), modifier = Modifier.fillMaxWidth()) { Text("Use tested candidate") }
                }
                if (fallback != null && state.active != fallback.path) {
                    TextButton(onClick = { viewModel.activate(fallback) }, enabled = !state.busy && !state.importing, modifier = Modifier.fillMaxWidth()) { Text("Restore fallback") }
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = { if (state.busy) viewModel.stop() else viewModel.run() },
                enabled = selected != null && !state.importing && !state.stopping,
                // Leave a generous gutter above Android vendor navigation regions.
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 44.dp)) {
                Text(if (state.stopping) "Waiting for current test…" else if (state.busy) "Stop after current test" else "Run selected model check")
            }
        }
    }
}
