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
    val latest = AppRelease("1.8.0", "2026-10-30", listOf(
        ReleaseHighlight(
            "Recurring tasks & habits",
            "Set tasks to repeat daily, weekdays (Mon–Fri), weekly, or monthly. Manage repeating tasks and routines directly from the task editor.",
            "Today → Your tasks"
        ),
        ReleaseHighlight(
            "Automatic next occurrence",
            "Marking a recurring task complete preserves the completed record and automatically schedules the next occurrence with updated due and reminder dates.",
            "Today → Your tasks"
        ),
        ReleaseHighlight(
            "Recurrence indicator & widget",
            "Glance at recurring tasks with a dedicated repeat icon in Today and cycle status badges on the Android home screen widget.",
            "Today; Home screen widget"
        ),
        ReleaseHighlight(
            "Offline privacy & backup",
            "Recurrence rules are computed purely locally without network or model inference, and are fully preserved across device reboots and local backups.",
            "Today; Backup & restore"
        )
    ))
}
