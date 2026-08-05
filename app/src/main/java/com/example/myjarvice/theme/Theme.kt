package com.example.myjarvice.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// --- Static color schemes -------------------------------------------------

private val DarkColors = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = Color(0xFF00272E),
    secondary = JarvisBlue,
    onSecondary = Color(0xFF001B3D),
    tertiary = ArcGold,
    onTertiary = Color(0xFF2A2100),
    background = JarvisDarkBackground,
    onBackground = TextPrimary,
    surface = JarvisSurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = JarvisSurfaceBorder,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF263A57)
)

private val AmoledColors = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = Color(0xFF00272E),
    secondary = JarvisBlue,
    onSecondary = Color(0xFF001B3D),
    tertiary = ArcGold,
    onTertiary = Color(0xFF2A2100),
    background = Color(0xFF000000),
    onBackground = TextPrimary,
    surface = Color(0xFF000000),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF0B0F16),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF1A2436)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0068B8),
    onPrimary = Color.White,
    secondary = Color(0xFF0077FF),
    onSecondary = Color.White,
    tertiary = Color(0xFFB88500),
    onTertiary = Color.White,
    background = Color(0xFFF4F8FE),
    onBackground = Color(0xFF0A1420),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A1420),
    surfaceVariant = Color(0xFFE4ECF7),
    onSurfaceVariant = Color(0xFF41546B),
    outline = Color(0xFFB6C6DD)
)

/**
 * Root theme. [themeMode] selects light/dark/AMOLED; [dynamicColor] overlays
 * Material You colors on Android 12+ (falls back to the static schemes below).
 */
@Composable
fun MyJarviceTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        themeMode == ThemeMode.AMOLED -> AmoledColors
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
