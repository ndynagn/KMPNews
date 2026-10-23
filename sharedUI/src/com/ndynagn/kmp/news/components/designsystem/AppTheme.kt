package com.ndynagn.kmp.news.components.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Expressive presentation theme. Hosts may supply a platform-derived color scheme. */
@Composable
fun AppTheme(
    colorScheme: ColorScheme = appColorScheme(isDark = isSystemInDarkTheme()),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(colorScheme = colorScheme, content = content)
}

/** Vivid azure fallback; a host-supplied system scheme replaces these roles as one complete palette. */
fun appColorScheme(isDark: Boolean): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = Color(0xFF5D93FF),
        onPrimary = Color(0xFF001D4D),
        primaryContainer = Color(0xFF0044AA),
        onPrimaryContainer = Color(0xFFDBE6FF),
        secondary = Color(0xFFADC8FF),
        onSecondary = Color(0xFF062657),
        secondaryContainer = Color(0xFF2C4470),
        onSecondaryContainer = Color(0xFFD9E6FF),
        tertiary = Color(0xFF68D8E0),
        onTertiary = Color(0xFF00363C),
        tertiaryContainer = Color(0xFF004F56),
        onTertiaryContainer = Color(0xFF99F0F6),
        primaryFixed = Color(0xFFD9E6FF),
        onPrimaryFixed = Color(0xFF001A41),
        primaryFixedDim = Color(0xFFADC8FF),
        onPrimaryFixedVariant = Color(0xFF004494),
        secondaryFixed = Color(0xFFD9E6FF),
        onSecondaryFixed = Color(0xFF122E58),
        secondaryFixedDim = Color(0xFFADC8FF),
        onSecondaryFixedVariant = Color(0xFF2C4470),
        tertiaryFixed = Color(0xFF99F0F6),
        onTertiaryFixed = Color(0xFF002023),
        tertiaryFixedDim = Color(0xFF68D8E0),
        onTertiaryFixedVariant = Color(0xFF004F56),
        background = Color(0xFF121418),
        onBackground = Color(0xFFE3E2E8),
        surface = Color(0xFF121418),
        onSurface = Color(0xFFE3E2E8),
        surfaceVariant = Color(0xFF45464F),
        onSurfaceVariant = Color(0xFFC5C5D0),
        surfaceTint = Color(0xFF5D93FF),
        inversePrimary = Color(0xFF0054CC),
        inverseSurface = Color(0xFFE3E2E8),
        inverseOnSurface = Color(0xFF2F3034),
        outline = Color(0xFF90909B),
        outlineVariant = Color(0xFF45464F),
        surfaceDim = Color(0xFF121418),
        surfaceBright = Color(0xFF383A40),
        surfaceContainerLowest = Color(0xFF0D0F13),
        surfaceContainerLow = Color(0xFF1A1C20),
        surfaceContainer = Color(0xFF1E2025),
        surfaceContainerHigh = Color(0xFF282A2F),
        surfaceContainerHighest = Color(0xFF33353A),
    )
} else {
    lightColorScheme(
        primary = Color(0xFF0064EF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD9E6FF),
        onPrimaryContainer = Color(0xFF001A41),
        secondary = Color(0xFF355E9C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD9E6FF),
        onSecondaryContainer = Color(0xFF122E58),
        tertiary = Color(0xFF006972),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF99F0F6),
        onTertiaryContainer = Color(0xFF002023),
        primaryFixed = Color(0xFFD9E6FF),
        onPrimaryFixed = Color(0xFF001A41),
        primaryFixedDim = Color(0xFFADC8FF),
        onPrimaryFixedVariant = Color(0xFF004494),
        secondaryFixed = Color(0xFFD9E6FF),
        onSecondaryFixed = Color(0xFF122E58),
        secondaryFixedDim = Color(0xFFADC8FF),
        onSecondaryFixedVariant = Color(0xFF2C4470),
        tertiaryFixed = Color(0xFF99F0F6),
        onTertiaryFixed = Color(0xFF002023),
        tertiaryFixedDim = Color(0xFF68D8E0),
        onTertiaryFixedVariant = Color(0xFF004F56),
        background = Color(0xFFFCFCFF),
        onBackground = Color(0xFF1A1C20),
        surface = Color(0xFFFCFCFF),
        onSurface = Color(0xFF1A1C20),
        surfaceVariant = Color(0xFFE3E2EC),
        onSurfaceVariant = Color(0xFF45464F),
        surfaceTint = Color(0xFF0064EF),
        inversePrimary = Color(0xFF82ACFF),
        inverseSurface = Color(0xFF2F3034),
        inverseOnSurface = Color(0xFFF2F0F4),
        outline = Color(0xFF767680),
        outlineVariant = Color(0xFFC5C5D0),
        surfaceDim = Color(0xFFDADAE0),
        surfaceBright = Color(0xFFFCFCFF),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF5F5FA),
        surfaceContainer = Color(0xFFEFEFF5),
        surfaceContainerHigh = Color(0xFFE9E9EF),
        surfaceContainerHighest = Color(0xFFE3E2E8),
    )
}

/** Shared semantic spacing; component-specific dimensions remain with their owner. */
object AppSpacing {
    val item = 8.dp
    val group = 16.dp
    val screen = 24.dp
}
