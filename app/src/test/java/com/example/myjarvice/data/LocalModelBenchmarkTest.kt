package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test

class LocalModelBenchmarkTest {
    private fun report(passes: Int, name: String = "baseline", time: Long = 1000) = BenchmarkReport(
        name, name, 0, "test-phone", 1, 4L * 1024 * 1024 * 1024, complete = true,
        cases = LocalModelBenchmark.questions.mapIndexed { index, question ->
            val answer = if (question.expected == null) "Take a small step." else if (index < passes) question.expected else "incorrect"
            BenchmarkCaseResult(question.id, question.label, answer, time, LocalModelBenchmark.matches(question, answer))
        })

    @Test fun strictlyBetterCandidateMayBePromoted() { assertTrue(LocalModelBenchmark.mayPromote(report(3), report(5, "candidate", 1400))) }
    @Test fun sameScoreCannotReplaceFallback() { assertFalse(LocalModelBenchmark.mayPromote(report(4), report(4, "candidate"))) }
    @Test fun slowCandidateCannotReplaceFallback() { assertFalse(LocalModelBenchmark.mayPromote(report(3), report(6, "candidate", 1600))) }
    @Test fun incompleteDifferentDeviceAndDuplicateCasesCannotPromote() {
        val baseline = report(3); val candidate = report(5, "candidate")
        assertFalse(LocalModelBenchmark.mayPromote(baseline, candidate.copy(complete = false)))
        assertFalse(LocalModelBenchmark.mayPromote(baseline, candidate.copy(device = "other")))
        assertFalse(LocalModelBenchmark.mayPromote(baseline, candidate.copy(cases = List(7) { candidate.cases.first() })))
        assertFalse(LocalModelBenchmark.mayPromote(baseline, candidate.copy(fingerprint = baseline.fingerprint)))
    }
    @Test fun scoreCannotBeForgedByFlags() {
        val candidate = report(3, "candidate")
        assertFalse(LocalModelBenchmark.mayPromote(report(2), candidate.copy(cases = candidate.cases.map { it.copy(passed = true) })))
    }
    @Test fun memoryGateStillAppliesToRenamedLargeCandidate() {
        val required = 3072L * 1024 * 1024
        assertEquals(required, LocalModelBenchmark.requiredMemoryBytes("qwen3-1.7b.litertlm", 977184032))
        assertEquals(required, LocalModelBenchmark.requiredMemoryBytes("renamed.litertlm", 977184032))
        assertTrue(LocalModelBenchmark.requiredMemoryBytes("gemma.litertlm", 584417280) < required)
    }
    @Test fun manualToneIsNotAutomaticPassAndExtraTextFails() {
        assertNull(LocalModelBenchmark.matches(LocalModelBenchmark.questions.last(), "Great answer"))
        assertFalse(LocalModelBenchmark.matches(LocalModelBenchmark.questions.first(), "BLUE because blue is nice")!!)
    }
    @Test fun qwenSoftSwitchAndReasoningCleanupAreModelSpecific() {
        assertTrue(LocalModelResponse.prompt("Qwen3-1.7B", "Hi").endsWith("/no_think"))
        assertEquals("Hi", LocalModelResponse.prompt("Gemma", "Hi"))
        assertEquals("BLUE", LocalModelResponse.answer("Qwen3", "<think>internal</think>BLUE"))
        assertEquals("", LocalModelResponse.answer("Qwen3", "<think>truncated"))
    }
}
