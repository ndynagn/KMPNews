package com.ndynagn.kmp.news.components.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
        val evidence = File(System.getProperty("java.io.tmpdir"), "kmpnews-compose-kit").apply { mkdirs() }

        for ((width, dark, fontScale) in listOf(Triple(360, false, 2f), Triple(900, true, 1f))) {
            val scene = ImageComposeScene(width, 1200, density = Density(1f, fontScale)) {
                AppTheme(if (dark) darkColorScheme() else lightColorScheme()) {
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
                        File(evidence, "jvm-$width-$fontScale.png").writeBytes(bytes)
                    } ?: error("Unable to encode rendered specimen")
                }
            } finally {
                scene.close()
            }
        }
    }
}
