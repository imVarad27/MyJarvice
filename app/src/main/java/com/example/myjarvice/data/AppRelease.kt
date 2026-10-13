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
    val latest = AppRelease("1.1.0", "2026-10-13", listOf(
        ReleaseHighlight("Know what's installed", "The real app version, build number and recent changes are now visible without a PC or network.",
            "Settings → About Jarvis → What's new"),
        ReleaseHighlight("Review before sharing", "Edit a completed reply, preview the exact text and approve opening Android's share sheet. Jarvis never chooses a recipient or presses Send.",
            "Completed chat reply → Review & share"),
        ReleaseHighlight("Find the right words", "Choose a contact name, edit it and prepare a draft request in your writing style. No address-book import or automatic sending.",
            "Today → Draft"),
        ReleaseHighlight("Make room in your week", "Read your next seven calendar days, spot overlapping busy events and inspect possible openings. Nothing is booked or edited.",
            "Today → Your calendar → Plan my week"),
        ReleaseHighlight("Protect your local backup", "Create a passphrase-protected backup and review a validated snapshot before merging it. Model files, credentials and voiceprints stay out.",
            "Settings → Data & storage")
    ))
}
