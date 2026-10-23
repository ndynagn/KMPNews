package com.ndynagn.kmp.news.components.designsystem

import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

class AppThemeTest {
    @Test
    fun selectedPaletteMaintainsReadableTextContrastInBothThemes() {
        for (isDark in listOf(false, true)) {
            val colors = appColorScheme(isDark)
            val pairs = listOf(
                colors.primary to colors.onPrimary,
                colors.primaryContainer to colors.onPrimaryContainer,
                colors.secondary to colors.onSecondary,
                colors.secondaryContainer to colors.onSecondaryContainer,
                colors.tertiary to colors.onTertiary,
                colors.tertiaryContainer to colors.onTertiaryContainer,
                colors.primaryFixed to colors.onPrimaryFixed,
                colors.primaryFixedDim to colors.onPrimaryFixedVariant,
                colors.secondaryFixed to colors.onSecondaryFixed,
                colors.secondaryFixedDim to colors.onSecondaryFixedVariant,
                colors.tertiaryFixed to colors.onTertiaryFixed,
                colors.tertiaryFixedDim to colors.onTertiaryFixedVariant,
                colors.inverseSurface to colors.inverseOnSurface,
                colors.inverseSurface to colors.inversePrimary,
                colors.surface to colors.onSurface,
                colors.surfaceContainerLow to colors.onSurface,
                colors.surfaceVariant to colors.onSurfaceVariant,
            )

            for ((background, content) in pairs) {
                val luminances = listOf(background.luminance(), content.luminance())
                val contrast = (luminances.max() + 0.05f) / (luminances.min() + 0.05f)

                assertTrue(contrast >= 4.5f, "Insufficient text contrast in dark=$isDark: $contrast")
            }
        }
    }
}
