package com.ndynagn.kmp.news

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ndynagn.kmp.news.components.designsystem.AppTheme

/** Android owns wallpaper colors; the shared kit receives only a color scheme. */
@Composable
internal fun androidColorScheme(useDynamicColor: Boolean = true): ColorScheme {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current

    return when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> darkColorScheme()

        else -> lightColorScheme()
    }
}

@Composable
internal fun AndroidAppTheme(useDynamicColor: Boolean = true, content: @Composable () -> Unit) {
    AppTheme(colorScheme = androidColorScheme(useDynamicColor), content = content)
}
