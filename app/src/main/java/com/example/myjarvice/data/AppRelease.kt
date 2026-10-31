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
    val latest = AppRelease("1.9.0", "2026-10-31", listOf(
        ReleaseHighlight(
            "Unified phone RAG search",
            "Ask Jarvis anything about your phone data. A unified local retrieval engine queries across tasks, routines, calendar events, saved notes, and memories in a single search.",
            "Chat; Voice mode"
        ),
        ReleaseHighlight(
            "Schedule & agenda query",
            "Ask what is on your schedule for today, tomorrow, or this week to get a combined chronological timeline of calendar meetings and due tasks.",
            "Chat; Voice mode"
        ),
        ReleaseHighlight(
            "Offline privacy & zero battery drain",
            "Retrieval uses fast on-device indexing with no heavy background embedding models, keeping your private phone content completely offline.",
            "Phone assistant"
        ),
        ReleaseHighlight(
            "Context-aware telemetry",
            "Phone task counts and schedule state are securely provided to assistant context so replies accurately reflect what's on your agenda.",
            "Phone & PC connection"
        )
    ))
}
