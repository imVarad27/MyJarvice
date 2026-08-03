package com.example.myjarvice.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisCyan,
    secondary = JarvisBlue,
    tertiary = ArcGold,
    background = JarvisDarkBackground,
    surface = JarvisSurfaceDark,
    onPrimary = JarvisDarkBackground,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun MyJarviceTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = Typography,
        content = content
    )
}
