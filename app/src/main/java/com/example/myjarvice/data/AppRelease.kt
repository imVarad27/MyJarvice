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
    val latest = AppRelease("1.3.0", "2026-10-21", listOf(
        ReleaseHighlight("Private routines", "Create reusable checklists for repeatable moments. Running a routine first shows every step, then adds private phone tasks only after you approve. Routines never send, open, or complete anything automatically.",
            "Today → Your tasks → Routines"),
        ReleaseHighlight("Your routines stay yours", "Routines work offline and are included in your local backup and restore preview. They are separate from PC tasks and never enter a model prompt.",
            "Today; Settings → Data & storage"),
        ReleaseHighlight("A calmer workspace", "Warm light and neutral dark surfaces, quieter message bubbles and a clearer composer keep the conversation first. Pixel, Jarvis, AMOLED and dynamic-color choices remain available.",
            "Chat; Settings → Appearance")
    ))
}
