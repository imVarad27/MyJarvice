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
    val latest = AppRelease("1.15.0", "2026-11-04", listOf(
        ReleaseHighlight(
            "Native email draft composer",
            "Prepare emails with recipient contact resolution, subject, and body drafted directly via assistant commands.",
            "Phone assistant"
        ),
        ReleaseHighlight(
            "Calendar event creator",
            "Open the calendar event composer with meeting titles and times pre-filled directly for quick saving.",
            "Phone assistant"
        ),
        ReleaseHighlight(
            "Enhanced hardware telemetry",
            "Live detection of battery saver mode, Bluetooth status, Do Not Disturb state, and precise network transport.",
            "Phone status; RAG"
        ),
        ReleaseHighlight(
            "Comprehensive phone control",
            "Unified voice and text tools across communication, calendar, media, settings, tasks, and system telemetry.",
            "Phone assistant"
        )
    ))
}
