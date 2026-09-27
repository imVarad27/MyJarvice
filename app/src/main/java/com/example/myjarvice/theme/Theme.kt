package com.example.myjarvice.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Jarvis uses the same blue, violet and teal roles across every screen.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFABC7FF),
    onPrimary = Color(0xFF092C65),
    primaryContainer = Color(0xFF25447F),
    onPrimaryContainer = Color(0xFFDFE9FF),
    secondary = Color(0xFFD0BFFF),
    onSecondary = Color(0xFF35205C),
    secondaryContainer = Color(0xFF403266),
    onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFF72DBCD),
    onTertiary = Color(0xFF003831),
    tertiaryContainer = Color(0xFF004F48),
    onTertiaryContainer = Color(0xFFA0F2E6),
    surfaceContainerLowest = Color(0xFF080F1C),
    surfaceContainerLow = Color(0xFF141E30),
    surfaceContainer = Color(0xFF19253A),
    surfaceContainerHigh = Color(0xFF233047),
    surfaceContainerHighest = Color(0xFF2D3B53),
    background = Color(0xFF0C1424),
    onBackground = Color(0xFFEDF2FF),
    surface = Color(0xFF0C1424),
    onSurface = Color(0xFFEDF2FF),
    surfaceVariant = Color(0xFF28364D),
    onSurfaceVariant = Color(0xFFBBC7DD),
    outline = Color(0xFF8794AC),
    outlineVariant = Color(0xFF3B4A63),
    surfaceTint = Color(0xFFABC7FF),
    inverseSurface = Color(0xFFE5ECFA),
    inverseOnSurface = Color(0xFF18243B),
    inversePrimary = Color(0xFF315EDA)
)

// Keep true black while retaining the same readable accents and containers.
private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C1320),
    surfaceContainer = Color(0xFF111B2C),
    surfaceContainerHigh = Color(0xFF1B283D),
    surfaceContainerHighest = Color(0xFF26344B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF315EDA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E9FF),
    onPrimaryContainer = Color(0xFF17366F),
    secondary = Color(0xFF6950BC),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE4FF),
    onSecondaryContainer = Color(0xFF432978),
    tertiary = Color(0xFF006B64),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC7F3EB),
    onTertiaryContainer = Color(0xFF005048),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F4FF),
    surfaceContainer = Color(0xFFEAF0FC),
    surfaceContainerHigh = Color(0xFFE3EAF8),
    surfaceContainerHighest = Color(0xFFDCE4F3),
    background = Color(0xFFF8FAFF),
    onBackground = Color(0xFF18243B),
    surface = Color(0xFFF8FAFF),
    onSurface = Color(0xFF18243B),
    surfaceVariant = Color(0xFFE3EAF8),
    onSurfaceVariant = Color(0xFF526079),
    outline = Color(0xFF75839B),
    outlineVariant = Color(0xFFCCD7EB),
    surfaceTint = Color(0xFF315EDA),
    inverseSurface = Color(0xFF26344B),
    inverseOnSurface = Color(0xFFEDF2FF),
    inversePrimary = Color(0xFFABC7FF)
)

/**
 * Root theme. [themeMode] selects light/dark/AMOLED; [dynamicColor] overlays
 * Material You colors on Android 12+ (falls back to the static schemes below).
 */
@Composable
fun MyJarvisTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    assistantStyle: AssistantStyle = AssistantStyle.PIXEL,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val context = LocalContext.current
    val baseColors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        themeMode == ThemeMode.AMOLED -> AmoledColors
        dark -> DarkColors
        else -> LightColors
    }

    val colorScheme = if (assistantStyle == AssistantStyle.JARVIS) baseColors.copy(
        primary = if (dark) Color(0xFF6CD9EE) else Color(0xFF00677B),
        onPrimary = if (dark) Color(0xFF003640) else Color.White,
        primaryContainer = if (dark) Color(0xFF074652) else Color(0xFFC2F0FA),
        onPrimaryContainer = if (dark) Color(0xFFC2F0FA) else Color(0xFF003640),
        surfaceTint = if (dark) Color(0xFF6CD9EE) else Color(0xFF00677B),
        inversePrimary = if (dark) Color(0xFF00677B) else Color(0xFF6CD9EE),
        tertiary = if (dark) Color(0xFFFFCE80) else Color(0xFF855400),
        onTertiary = if (dark) Color(0xFF462A00) else Color.White,
        tertiaryContainer = if (dark) Color(0xFF634000) else Color(0xFFFFE7BE),
        onTertiaryContainer = if (dark) Color(0xFFFFE7BE) else Color(0xFF573600)
    ) else baseColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp)),
        content = content
    )
}

// Backward-compatibility alias
@Composable
fun MyJarviceTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    assistantStyle: AssistantStyle = AssistantStyle.PIXEL,
    content: @Composable () -> Unit
) = MyJarvisTheme(themeMode = themeMode, dynamicColor = dynamicColor, assistantStyle = assistantStyle, content = content)
