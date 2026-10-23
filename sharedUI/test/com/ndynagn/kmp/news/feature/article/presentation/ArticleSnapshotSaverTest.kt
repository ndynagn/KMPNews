package com.ndynagn.kmp.news.feature.article.presentation

import androidx.compose.runtime.saveable.SaverScope
import com.ndynagn.kmp.news.feature.feed.domain.Article
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ArticleSnapshotSaverTest {
    @Test
    fun restoresCompleteAndMissingFieldSnapshotsWithoutLookingUpTheFeed() {
        val scope = SaverScope { true }
        val articles = listOf(
            Article("missing"),
            Article(
                id = "full",
                title = "Title",
                url = "https://example.com",
                summary = "Description",
                imageUrl = "https://example.com/image",
                sourceId = "id",
                sourceName = "News",
                publishedAtEpochMilliseconds = 42L,
            ),
        )

        for (article in articles) {
            val saved = with(articleSnapshotSaver) { scope.save(article) }
            val restored = articleSnapshotSaver.restore(assertNotNull(saved))

            assertEquals(article, restored)
        }
    }
}
