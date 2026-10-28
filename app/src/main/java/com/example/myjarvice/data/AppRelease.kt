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
    val latest = AppRelease("1.7.0", "2026-10-28", listOf(
        ReleaseHighlight(
            "PC pairing setup code import",
            "Pair your phone and PC in seconds. Paste a jarvis-pair link, JSON config, or .env snippet to automatically configure your PC host address and pairing token.",
            "Settings → PC connection; Chat → Connect PC"
        ),
        ReleaseHighlight(
            "Live connection test before apply",
            "Test host reachability directly inside the setup dialog before applying changes, with masked token preview for peace of mind.",
            "Settings → PC connection"
        ),
        ReleaseHighlight(
            "PC terminal pairing helper",
            "Run 'python -m server.pair_helper' on your PC to discover local network IPv4 addresses and generate one-click pairing links and setup snippets.",
            "Jarvis PC Server"
        ),
        ReleaseHighlight(
            "Backup safety status",
            "Clear confirmation of when your local Jarvis data was last backed up, helping you keep your conversations, tasks, and routines safe.",
            "Settings → Data & storage"
        )
    ))
}
