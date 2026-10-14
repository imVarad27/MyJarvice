package com.example.myjarvice.data

/** Static release metadata only: never includes device identifiers, hosts, models or user content. */
internal data class AppVersionInfo(val versionName: String, val versionCode: Int, val developmentBuild: Boolean) {
    val buildLabel: String get() = if (developmentBuild) "Development build" else "Release build"
    val summary: String get() = "Jarvis $versionName · build $versionCode · $buildLabel"
}

internal data class ReleaseHighlight(val title: String, val description: String, val location: String)

/** Append a new entry when shipping a feature release; never relabel historical rolling builds. */
internal data class AppRelease(val version: String, val date: String, val highlights: List<ReleaseHighlight>)

internal object JarvisReleases {
    val latest = AppRelease("1.2.0", "2026-10-14", listOf(
        ReleaseHighlight("Rehearse before saying yes", "Try a 30-, 60- or 90-minute commitment against your visible calendar, with preparation and recovery buffers. Nothing is booked or sent to a model.",
            "Today → Your calendar → Plan my week → Time Rehearsal"),
        ReleaseHighlight("Inspect the margin", "A same-start stress test shows which sampled slots also fit a 50% overrun. Filter to overrun-safe options; this is an explicit assumption, not a prediction or exhaustive availability check.",
            "Time Rehearsal → Rehearse a commitment"),
        ReleaseHighlight("Private by construction", "Rehearsals use read-only calendar data on the phone. Incomplete data disables results. Scenarios disappear when you leave; no events, reminders or tasks are changed.",
            "Time Rehearsal → Preparation and recovery")
    ))
}
