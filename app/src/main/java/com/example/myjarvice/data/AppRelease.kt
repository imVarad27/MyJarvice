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
    val latest = AppRelease("1.13.0", "2026-11-04", listOf(
        ReleaseHighlight(
            "Task completion by voice or chat",
            "Mark open tasks as completed by name or keyword directly through Jarvis, seamlessly triggering recurring tasks.",
            "Chat; Voice mode"
        ),
        ReleaseHighlight(
            "Media playback control",
            "Play, pause, skip, or resume music and media playback directly through assistant commands.",
            "Phone assistant"
        ),
        ReleaseHighlight(
            "Ringer mode control",
            "Switch phone sound modes between Normal, Vibrate, or Silent, or check the current ringer mode.",
            "Phone assistant"
        ),
        ReleaseHighlight(
            "Full device action suite",
            "Control phone media, tasks, settings, clipboard, and apps effortlessly using on-device deterministic tools.",
            "Phone assistant"
        )
    ))
}
