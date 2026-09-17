package com.example.myjarvice.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import com.example.myjarvice.wake.WakeEvents
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

object LocalBenchmarkRuntime { val active = MutableStateFlow(false) }

class LocalBenchmarkRunner(private val context: Context) {
    suspend fun run(model: File, progress: suspend (BenchmarkReport) -> Unit): BenchmarkReport = withContext(Dispatchers.Default) {
        check(LocalBenchmarkRuntime.active.compareAndSet(false, true)) { "A comparison is already running." }
        val library = LocalModelLibrary(context)
        val engine = OnDeviceInferenceEngine(context)
        var report = BenchmarkReport(model.name, library.fingerprint(model), System.currentTimeMillis(),
            "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.SDK_INT}", model.length(), 0)
        suspend fun publish() { library.save(model, report); progress(report) }
        WakeEvents.setMicrophoneBusy("local-benchmark", true)
        try {
            OnDeviceInferenceEngine.releaseIdleModels()
            val info = memory(context)
            val required = LocalModelBenchmark.requiredMemoryBytes(model.name, model.length())
            report = report.copy(availableBeforeBytes = info.availMem)
            if (info.lowMemory || info.availMem < required) {
                report = report.copy(note = "Safety check blocked loading: needs ${gib(required)} GB available RAM; found ${gib(info.availMem)} GB. Close other apps and try again. Your active model is unchanged.")
                publish()
                return@withContext report
            }
            report = report.copy(note = "Running synthetic tests. Wake listening is temporarily paused.")
            publish()
            for (question in LocalModelBenchmark.questions) {
                currentCoroutineContext().ensureActive()
                val remaining = memory(context)
                if (remaining.lowMemory || remaining.availMem < 256L * 1024 * 1024) {
                    report = report.copy(note = "Stopped because available memory became too low. No model switch was made.")
                    break
                }
                val start = SystemClock.elapsedRealtime()
                val result = engine.benchmark(model, question.prompt)
                val elapsed = SystemClock.elapsedRealtime() - start
                val answer = result.getOrDefault("").take(2000)
                report = report.copy(cases = report.cases + BenchmarkCaseResult(question.id, question.label, answer,
                    elapsed, if (result.isSuccess) LocalModelBenchmark.matches(question, answer) else false,
                    result.exceptionOrNull()?.message?.take(300)), maxObservedPssKb = maxOf(report.maxObservedPssKb, Debug.getPss()))
                publish()
                if (result.isFailure || elapsed > 45_000) {
                    report = report.copy(note = if (result.isFailure) "Runtime test failed. Active model unchanged." else "Stopped after a test took over 45 seconds. This model is not responsive enough for this check.")
                    break
                }
            }
            val complete = report.cases.size == LocalModelBenchmark.questions.size && report.cases.none { it.error != null }
            report = report.copy(complete = complete, note = if (complete)
                "Finished. Six checks are automatic; conversation tone needs your review. First-test timing includes model startup." else report.note)
            publish()
            report
        } catch (cancelled: CancellationException) {
            report = report.copy(complete = false, note = "Stopped after the current native test. You can rerun the comparison.")
            withContext(NonCancellable) { library.save(model, report) }
            throw cancelled
        } finally {
            // Native calls cannot be interrupted safely: cleanup waits until the call has returned.
            withContext(NonCancellable) { OnDeviceInferenceEngine.releaseIdleModels() }
            engine.close()
            WakeEvents.setMicrophoneBusy("local-benchmark", false)
            LocalBenchmarkRuntime.active.value = false
        }
    }

    companion object {
        fun memory(context: Context) = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        fun gib(bytes: Long) = String.format(java.util.Locale.US, "%.1f", bytes / (1024.0 * 1024 * 1024))
    }
}
