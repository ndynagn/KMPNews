package com.ndynagn.kmp.news.feature.componentcatalog

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.ndynagn.kmp.news.R
import com.ndynagn.kmp.news.components.designsystem.AppActionButton
import com.ndynagn.kmp.news.components.designsystem.AppActionEmphasis
import com.ndynagn.kmp.news.components.designsystem.AppNewsPreview
import com.ndynagn.kmp.news.components.designsystem.AppPasswordField
import com.ndynagn.kmp.news.components.designsystem.AppSpacing
import com.ndynagn.kmp.news.components.designsystem.AppStatusView
import com.ndynagn.kmp.news.components.designsystem.AppTheme
import com.ndynagn.kmp.news.components.designsystem.appColorScheme
import java.io.File
import java.util.Locale
import org.junit.Rule
import org.junit.Test

/** Renders the selected components with explicit theme/locale inputs, without changing device settings. */
class SelectedKitTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectedComponentsRenderInBothThemesRussianAndLargeText() {
        var isDark by mutableStateOf(false)
        var fontScale by mutableStateOf(1f)
        var language by mutableStateOf("en")
        var useSystemExample by mutableStateOf(false)
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            val context = LocalContext.current
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale(language)) }
            val localizedContext = context.createConfigurationContext(configuration)
            val density = LocalDensity.current

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                val scheme = if (useSystemExample) {
                    lightColorScheme(
                        primary = Color(0xFF795900),
                        onPrimary = Color.White,
                        primaryContainer = Color(0xFFFFDF93),
                        onPrimaryContainer = Color(0xFF261900),
                        secondary = Color(0xFF6A5D3F),
                        onSecondary = Color.White,
                        secondaryContainer = Color(0xFFF3E0BB),
                        onSecondaryContainer = Color(0xFF241A04),
                        surfaceTint = Color(0xFF795900),
                    )
                } else {
                    appColorScheme(isDark)
                }

                AppTheme(scheme) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()).padding(AppSpacing.screen),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.group),
                        ) {
                            AppPasswordField("fictional", {}, stringResource(R.string.catalog_password))
                            AppActionEmphasis.entries.forEach { emphasis ->
                                AppActionButton(
                                    stringResource(R.string.catalog_primary),
                                    {},
                                    Modifier.fillMaxWidth(),
                                    emphasis = emphasis,
                                )
                            }
                            AppActionButton(stringResource(R.string.catalog_primary), {}, isBusy = true)
                            AppNewsPreview(
                                stringResource(R.string.catalog_article),
                                Modifier.fillMaxWidth().testTag("selected.news"),
                                metadata = stringResource(R.string.catalog_article_metadata),
                                image = {
                                    Image(
                                        painterResource(R.drawable.catalog_observatory),
                                        stringResource(R.string.catalog_observatory),
                                        Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                },
                            )
                            AppNewsPreview(
                                stringResource(R.string.catalog_article),
                                Modifier.testTag("selected.compact"),
                                metadata = stringResource(R.string.catalog_article_metadata),
                                isCompact = true,
                            )
                            AppStatusView(
                                stringResource(R.string.catalog_request_error),
                                Modifier.testTag("selected.error"),
                                message = stringResource(R.string.catalog_body),
                            ) {
                                AppActionButton(
                                    stringResource(R.string.catalog_retry),
                                    {},
                                    emphasis = AppActionEmphasis.TEXT,
                                )
                            }
                        }
                    }
                }
            }
        }

        for ((dark, scale, locale) in listOf(Triple(false, 1f, "en"), Triple(true, 1f, "ru"), Triple(true, 2f, "ru"))) {
            compose.runOnIdle {
                isDark = dark
                fontScale = scale
                language = locale
            }

            for (specimen in listOf("news", "compact", "error")) {
                compose.onNodeWithTag("selected.$specimen").performScrollTo().assertIsDisplayed()
                capture("$specimen-$dark-$scale-$locale")
            }
        }

        compose.runOnIdle {
            isDark = false
            fontScale = 1f
            language = "en"
            useSystemExample = true
        }
        compose.onNodeWithTag("selected.news").performScrollTo().assertIsDisplayed()
        capture("injected-system-example")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.filesDir, "selected-kit-evidence").apply { mkdirs() }

        File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
