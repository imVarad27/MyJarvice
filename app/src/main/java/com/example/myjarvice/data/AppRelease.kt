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
    val latest = AppRelease("1.12.0", "2026-11-03", listOf(
        ReleaseHighlight(
            "Quick note & link capture",
            "Save thoughts, links, and text directly into your private phone inbox with voice or chat, indexed immediately across phone search.",
            "Chat; Voice mode"
        ),
        ReleaseHighlight(
            "Instant camera launch",
            "Open the camera or prepare photo capture directly through assistant voice commands.",
            "Phone assistant"
        ),
        ReleaseHighlight(
            "Nearby places & Maps search",
            "Search for nearby businesses, gas stations, or places of interest directly in Google Maps.",
            "Phone assistant"
        ),
        ReleaseHighlight(
            "Clock alarms & timers inspectors",
            "Jump straight to your active alarms or timers in the Android Clock app.",
            "Phone assistant"
        )
    ))
}
