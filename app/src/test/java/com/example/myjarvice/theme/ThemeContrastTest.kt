package com.example.myjarvice.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    private fun contrast(a: Color, b: Color): Float = (maxOf(a.luminance(), b.luminance()) + .05f) /
        (minOf(a.luminance(), b.luminance()) + .05f)
    private fun readable(scheme: ColorScheme) {
        for (surface in listOf(scheme.background, scheme.surface, scheme.surfaceContainerLow, scheme.surfaceContainerHigh)) {
            assertTrue("Body text contrast", contrast(scheme.onSurface, surface) >= 4.5f)
            assertTrue("Supporting text contrast", contrast(scheme.onSurfaceVariant, surface) >= 4.5f)
        }
        assertTrue("Primary action contrast", contrast(scheme.primary, scheme.onPrimary) >= 4.5f)
        assertTrue("Neutral composer action contrast", contrast(scheme.onSurface, scheme.surface) >= 4.5f)
        assertTrue("Error text contrast", contrast(scheme.error, scheme.surface) >= 4.5f)
    }
    @Test fun lightPaletteHasReadableTextAndActions() { readable(LightColors) }
    @Test fun darkPaletteHasReadableTextAndActions() { readable(DarkColors) }
    @Test fun amoledPaletteHasReadableTextAndActions() { readable(AmoledColors) }
}
