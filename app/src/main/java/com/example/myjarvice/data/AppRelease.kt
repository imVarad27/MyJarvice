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
    val latest = AppRelease("1.16.0", "2026-11-04", listOf(
        ReleaseHighlight(
            "Task deletion tool",
            "Delete completed or obsolete phone tasks by voice or model tool calls with title and keyword matching.",
            "Phone tasks"
        ),
        ReleaseHighlight(
            "Battery diagnostics inspector",
            "Check detailed battery health, temperature in °C, charging source (AC, USB, Wireless), and power saver status.",
            "Phone status"
        ),
        ReleaseHighlight(
            "Next alarm clock telemetry & RAG",
            "Automatically query upcoming alarm clock schedules and index them into offline RAG search.",
            "Phone search; RAG"
        ),
        ReleaseHighlight(
            "Comprehensive phone control",
            "Unified voice and text tools across communication, calendar, media, settings, tasks, and system telemetry.",
            "Phone assistant"
        )
    ))
}
