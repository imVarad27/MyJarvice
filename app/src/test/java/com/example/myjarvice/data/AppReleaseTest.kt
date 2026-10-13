package com.example.myjarvice.data

import com.example.myjarvice.BuildConfig
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Locale

class AppReleaseTest {
    @Test fun `release notes must match the generated APK version`() {
        assertEquals("Update JarvisReleases.latest when bumping version.properties", BuildConfig.VERSION_NAME, JarvisReleases.latest.version)
        assertTrue(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(BuildConfig.VERSION_NAME))
        assertTrue(BuildConfig.VERSION_CODE in 1..2_100_000_000)
    }

    @Test fun `version report contains only explicit build metadata`() {
        val version = AppVersionInfo("2.3.4", 99, true)
        assertEquals("Jarvis 2.3.4 · build 99 · Development build", version.summary)
        assertEquals("Release build", version.copy(developmentBuild = false).buildLabel)
    }

    @Test fun `release highlights are nonempty bounded discoverable and have a valid date`() {
        val release = JarvisReleases.latest
        val position = ParsePosition(0)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(release.date, position)
        assertNotNull(date)
        assertEquals(release.date.length, position.index)
        assertTrue(release.highlights.size in 1..12)
        assertEquals(release.highlights.size, release.highlights.map { it.title }.distinct().size)
        release.highlights.forEach {
            assertTrue(it.title.isNotBlank() && it.title.length <= 100)
            assertTrue(it.description.isNotBlank() && it.description.length <= 500)
            assertTrue(it.location.isNotBlank() && it.location.length <= 160)
        }
    }
}
