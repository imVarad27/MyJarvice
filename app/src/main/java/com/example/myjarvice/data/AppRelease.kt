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
    val latest = AppRelease("1.6.0", "2026-10-26", listOf(
        ReleaseHighlight(
            "Today home screen widget",
            "Glance at your top open tasks and due dates right from your Android home screen. Automatically updates as you manage tasks in Jarvis.",
            "Android home screen"
        ),
        ReleaseHighlight(
            "Instant quick actions",
            "One-tap shortcuts on your home screen to launch hands-free voice mode, capture a quick note, or jump straight into Today.",
            "Android home screen; Today"
        ),
        ReleaseHighlight(
            "Private task reminders",
            "Schedule optional notifications for private phone tasks. Reminders are managed directly in the task editor with clear presets, custom date/time, and explicit cancellation.",
            "Today → Your tasks"
        ),
        ReleaseHighlight(
            "Explicit permission and privacy",
            "Task reminders stay local and offline. They require explicit notification permission, make no model or network calls, and are restored across device reboots and local backups.",
            "Today → Your tasks; Backup"
        )
    ))
}
