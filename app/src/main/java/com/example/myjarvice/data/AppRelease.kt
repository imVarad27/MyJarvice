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
    val latest = AppRelease("1.3.1", "2026-10-23", listOf(
        ReleaseHighlight(
            "Resilient live connection",
            "Safe background alert dispatching and automatic cleanup of disconnected sockets. Prevents server thread pool exhaustion when network connections drop unexpectedly.",
            "Host connection; Alerts"
        ),
        ReleaseHighlight(
            "Stream error recovery",
            "Resilient token emission catches sudden client drops during active model response generation without interrupting host inference threads.",
            "Chat; Host connection"
        ),
        ReleaseHighlight(
            "Routine briefing stability",
            "Fixed log formatting and error reporting during routine morning briefing weather retrieval.",
            "Routine briefing; Today"
        )
    ))
}
