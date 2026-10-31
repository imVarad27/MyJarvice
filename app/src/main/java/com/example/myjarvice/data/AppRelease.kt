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
    val latest = AppRelease("1.10.0", "2026-10-31", listOf(
        ReleaseHighlight(
            "Phone contacts search & reverse lookup",
            "Ask Jarvis to find contact phone numbers, emails, or look up who a phone number belongs to with private, on-device contacts search.",
            "Chat; Voice mode"
        ),
        ReleaseHighlight(
            "Dialer & messaging prep",
            "Prepare calls and SMS drafts directly through Jarvis. Dial numbers or compose messages in native phone apps for easy user confirmation.",
            "Phone assistant"
        ),
        ReleaseHighlight(
            "Installed apps explorer",
            "Query and discover installed launchable apps by keyword or category to effortlessly launch applications with voice or chat.",
            "Phone assistant"
        ),
        ReleaseHighlight(
            "Unified contacts RAG indexing",
            "Contacts are seamlessly integrated into unified phone search, letting you find people alongside tasks, calendar events, routines, and notes.",
            "Phone assistant"
        )
    ))
}
