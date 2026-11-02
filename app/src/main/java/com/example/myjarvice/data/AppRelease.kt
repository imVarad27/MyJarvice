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
    val latest = AppRelease("1.11.0", "2026-11-02", listOf(
        ReleaseHighlight(
            "Volume & sound control",
            "Adjust phone media volume, mute, or check current levels with native on-screen volume slider feedback.",
            "Chat; Voice mode"
        ),
        ReleaseHighlight(
            "Settings shortcuts",
            "Instantly jump to Wi-Fi, Bluetooth, Display, Sound, Battery, Apps, or Date & Time system settings.",
            "Phone assistant"
        ),
        ReleaseHighlight(
            "Clipboard assistant",
            "Read copied text from the phone clipboard or copy generated answers and notes directly to clipboard.",
            "Phone assistant"
        ),
        ReleaseHighlight(
            "Web search & hardware telemetry",
            "Open websites or web searches in the phone browser, and monitor live storage and RAM capacity in phone status.",
            "Phone assistant"
        )
    ))
}
