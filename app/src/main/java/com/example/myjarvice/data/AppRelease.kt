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
    val latest = AppRelease("1.14.0", "2026-11-04", listOf(
        ReleaseHighlight(
            "Installed apps in phone RAG",
            "Jarvis now indexes all installed launcher applications in offline phone search to instantly answer queries about your apps.",
            "Phone search; RAG"
        ),
        ReleaseHighlight(
            "Live phone status & 30-day calendar RAG",
            "Offline RAG now includes live hardware telemetry (battery, charging, storage, RAM) and an expanded 30-day calendar horizon.",
            "Phone search; RAG"
        ),
        ReleaseHighlight(
            "WhatsApp draft composer",
            "Draft and review WhatsApp messages with contacts directly using voice or text without manual typing.",
            "Phone assistant"
        ),
        ReleaseHighlight(
            "Google web search tool",
            "Instantly search the web or Google for answers and topics directly from assistant commands.",
            "Phone assistant"
        )
    ))
}
