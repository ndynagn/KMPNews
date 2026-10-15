package com.ndynagn.kmp.news.components.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Expressive presentation theme. Hosts may supply a platform-derived color scheme. */
@Composable
fun AppTheme(
    colorScheme: ColorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(colorScheme = colorScheme, content = content)
}

/** Shared semantic spacing; component-specific dimensions remain with their owner. */
object AppSpacing {
    val item = 8.dp
    val group = 16.dp
    val screen = 24.dp
}
