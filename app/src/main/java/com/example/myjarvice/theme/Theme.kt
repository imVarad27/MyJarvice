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

// Neutral surfaces keep content calm; indigo/cyan accents identify actions and state.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFBBC3FF),
    onPrimary = Color(0xFF202B78),
    primaryContainer = Color(0xFF343F91),
    onPrimaryContainer = Color(0xFFE2E5FF),
    secondary = Color(0xFFC8C4DA),
    onSecondary = Color(0xFF302F3D),
    secondaryContainer = Color(0xFF464452),
    onSecondaryContainer = Color(0xFFE5E1F4),
    tertiary = Color(0xFF8CD7DF),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF164E54),
    onTertiaryContainer = Color(0xFFB6F0F5),
    surfaceContainerLowest = Color(0xFF07090D),
    surfaceContainerLow = Color(0xFF11141B),
    surfaceContainer = Color(0xFF171A22),
    surfaceContainerHigh = Color(0xFF1D212B),
    surfaceContainerHighest = Color(0xFF252A36),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFE4E5EC),
    surface = Color(0xFF0B0D12),
    onSurface = Color(0xFFE4E5EC),
    surfaceVariant = Color(0xFF252A36),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E909B),
    outlineVariant = Color(0xFF3E424D),
    surfaceTint = Color(0xFFBBC3FF),
    inverseSurface = Color(0xFFE4E5EC),
    inverseOnSurface = Color(0xFF2B2E36),
    inversePrimary = Color(0xFF4E5FC7)
)

// Keep true black while retaining the same readable accents and containers.
private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF090B10),
    surfaceContainer = Color(0xFF10131A),
    surfaceContainerHigh = Color(0xFF171B24),
    surfaceContainerHighest = Color(0xFF202530)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4E5FC7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E5FF),
    onPrimaryContainer = Color(0xFF283586),
    secondary = Color(0xFF5E5C6B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E1F0),
    onSecondaryContainer = Color(0xFF454250),
    tertiary = Color(0xFF006970),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA7F0F5),
    onTertiaryContainer = Color(0xFF004F55),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F5FA),
    surfaceContainer = Color(0xFFEFF0F6),
    surfaceContainerHigh = Color(0xFFE9EAF0),
    surfaceContainerHighest = Color(0xFFE2E3EA),
    background = Color(0xFFFAF9FE),
    onBackground = Color(0xFF1B1B20),
    surface = Color(0xFFFAF9FE),
    onSurface = Color(0xFF1B1B20),
    surfaceVariant = Color(0xFFE2E3EA),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C6D0),
    surfaceTint = Color(0xFF4E5FC7),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF2F0F7),
    inversePrimary = Color(0xFFBBC3FF)
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
        shapes = Shapes(extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp)),
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
