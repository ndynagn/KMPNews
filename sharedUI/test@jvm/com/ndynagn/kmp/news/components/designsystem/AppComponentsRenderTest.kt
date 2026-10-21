package com.ndynagn.kmp.news.components.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Headless rendering verifies shared components without adding a Desktop product route. */
@OptIn(ExperimentalComposeUiApi::class)
class AppComponentsRenderTest {
    @Test
    fun rendersNarrowLargeTextAndWideDarkSpecimens() {
        val evidence = File(System.getProperty("java.io.tmpdir"), "kmpnews-selected-kit").apply { mkdirs() }

        val scenarios = listOf(
            Triple(390, false, 1f),
            Triple(390, true, 1f),
            Triple(360, false, 2f),
            Triple(900, true, 1f),
        )

        for ((width, dark, fontScale) in scenarios) {
            val scene = ImageComposeScene(width, 1200, density = Density(1f, fontScale)) {
                AppTheme(appColorScheme(isDark = dark)) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.padding(AppSpacing.screen),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.group),
                        ) {
                            AppActionButton(
                                "A long action title that wraps naturally",
                                {},
                                Modifier.fillMaxWidth(),
                                isBusy = true,
                            )
                            AppPasswordField("example", {}, "Password", Modifier.fillMaxWidth())
                            AppNewsPreview(
                                "An article with optional metadata",
                                metadata = "Demo News",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            AppNewsPreview(
                                "A compact news headline",
                                metadata = "Demo News",
                                isCompact = true,
                                image = { AppImagePlaceholder("Image unavailable") },
                            )
                            AppStatusView(
                                "Unable to load news",
                                message = "Try again when a connection is available.",
                            ) {
                                AppActionButton("Retry", {}, emphasis = AppActionEmphasis.TEXT)
                            }
                        }
                    }
                }
            }

            try {
                scene.render().close()
                scene.render(500_000_000L).use { image ->
                    image.encodeToData()?.use { data ->
                        val bytes = data.bytes

                        assertTrue(bytes.size > 1000, "The specimen must produce a populated raster image")
                        File(evidence, "jvm-$width-$dark-$fontScale.png").writeBytes(bytes)
                    } ?: error("Unable to encode rendered specimen")
                }
            } finally {
                scene.close()
            }
        }
    }
}
