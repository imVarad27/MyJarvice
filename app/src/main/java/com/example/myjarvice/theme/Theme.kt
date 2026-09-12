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

// --- Obsidian Dark (Google Gemini / ChatGPT standard) ---
private val DarkColors = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF123C54),
    onPrimaryContainer = Color(0xFFBCE9FF),
    secondaryContainer = Color(0xFF263746),
    onSecondaryContainer = Color(0xFFDBEAFE),
    surfaceContainerLowest = Color(0xFF101112),
    surfaceContainerLow = Color(0xFF191B1E),
    surfaceContainer = Color(0xFF1E1F20),
    surfaceContainerHigh = Color(0xFF25282C),
    surfaceContainerHighest = Color(0xFF2D3136),
    secondary = JarvisBlue,
    onSecondary = Color(0xFF172554),
    tertiary = ArcGold,
    onTertiary = Color(0xFF451A03),
    background = Color(0xFF131314),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF1E1F20),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF282A2C),
    onSurfaceVariant = Color(0xFFADB3BC),
    outline = Color(0xFF626975),
    outlineVariant = Color(0xFF363C46)
)

// --- True Black (AMOLED) ---
private val AmoledColors = darkColorScheme(
    primary = JarvisCyan,
    primaryContainer = Color(0xFF123C54),
    onPrimaryContainer = Color(0xFFBCE9FF),
    secondaryContainer = Color(0xFF202C38),
    onSecondaryContainer = Color(0xFFDBEAFE),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF08090A),
    surfaceContainer = Color(0xFF101012),
    surfaceContainerHigh = Color(0xFF181A1E),
    surfaceContainerHighest = Color(0xFF22252A),
    onPrimary = Color(0xFF082F49),
    secondary = JarvisBlue,
    onSecondary = Color(0xFF172554),
    tertiary = ArcGold,
    onTertiary = Color(0xFF451A03),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF101012),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF1A1A1E),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF626975),
    outlineVariant = Color(0xFF30343B)
)

// --- Refined Light (Gemini Light) ---
private val LightColors = lightColorScheme(
    primary = Color(0xFF03699E),
    primaryContainer = Color(0xFFDDEFFB),
    onPrimaryContainer = Color(0xFF10354E),
    secondaryContainer = Color(0xFFE3EBF4),
    onSecondaryContainer = Color(0xFF20364C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F7FA),
    surfaceContainer = Color(0xFFF0F3F7),
    surfaceContainerHigh = Color(0xFFEAF0F5),
    surfaceContainerHighest = Color(0xFFE3E9EF),
    onPrimary = Color.White,
    secondary = Color(0xFF2563EB),
    onSecondary = Color.White,
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFECEEF1),
    onSurfaceVariant = Color(0xFF5E6266),
    outline = Color(0xFF747C88),
    outlineVariant = Color(0xFFD3D9E2)
)

/**
 * Root theme. [themeMode] selects light/dark/AMOLED; [dynamicColor] overlays
 * Material You colors on Android 12+ (falls back to the static schemes below).
 */
@Composable
fun MyJarvisTheme(
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

// Backward-compatibility alias
@Composable
fun MyJarviceTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = MyJarvisTheme(themeMode = themeMode, dynamicColor = dynamicColor, content = content)
