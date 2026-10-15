package com.ndynagn.kmp.news.feature.feed.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.ndynagn.kmp.news.components.designsystem.AppTheme
import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.feature.feed.domain.FeedSnapshot
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Production screen rendered headlessly; no Desktop route or application fixtures are added. */
@OptIn(ExperimentalComposeUiApi::class)
class FeedRenderTest {
    @Test
    fun rendersAdaptiveFeedAtPhoneAndWideWidthsWithLargeText() {
        val evidence = File(System.getProperty("java.io.tmpdir"), "kmpnews-compose-feed").apply { mkdirs() }
        val articles = (0..8).map { index ->
            Article(
                "fixture-$index",
                title = "A long news headline that grows naturally with larger text $index",
                sourceName = "Test source",
                publishedAtEpochMilliseconds = 10,
            )
        }
        val feed = FeedUiState.Content(FeedSnapshot(articles, 10, false, false), FeedStatus())

        for ((width, dark, scale) in listOf(Triple(390, false, 1f), Triple(390, true, 2f), Triple(1024, true, 2f))) {
            val scene = ImageComposeScene(width, 844, density = Density(1f, scale)) {
                AppTheme(if (dark) darkColorScheme() else lightColorScheme()) {
                    Surface(Modifier.fillMaxSize()) {
                        FeedScreen(feed, rememberLazyGridState(), {}, { "14 October 2026" })
                    }
                }
            }

            try {
                scene.render().close()
                scene.render(500_000_000L).use { raster ->
                    raster.encodeToData()?.use { data ->
                        assertTrue(data.bytes.size > 1000)
                        File(evidence, "feed-$width-$scale.png").writeBytes(data.bytes)
                    } ?: error("Unable to encode feed")
                }
            } finally {
                scene.close()
            }
        }
    }
}
