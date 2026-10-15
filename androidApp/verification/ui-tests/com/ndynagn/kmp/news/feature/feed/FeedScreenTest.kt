package com.ndynagn.kmp.news.feature.feed

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.ndynagn.kmp.news.androidColorScheme
import com.ndynagn.kmp.news.feature.auth.FakeAuthRepository
import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.feature.feed.domain.FeedClock
import com.ndynagn.kmp.news.feature.feed.domain.FeedFailure
import com.ndynagn.kmp.news.feature.feed.domain.FeedReadResult
import com.ndynagn.kmp.news.feature.feed.domain.FeedSnapshot
import com.ndynagn.kmp.news.feature.feed.domain.FeedUpdateResult
import com.ndynagn.kmp.news.feature.feed.domain.NewsRepository
import com.ndynagn.kmp.news.feature.feed.domain.RefreshFeedIfNeeded
import com.ndynagn.kmp.news.feature.home.presentation.MobileApp
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class FeedScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun missingImageAndMetadataUseReadableFallbackWithoutHidingTheCard() {
        val repository = FakeScreenRepository(
            articles = listOf(
                Article("missing", imageUrl = "file:///nonexistent-feed-test-image.png"),
            ),
        )
        compose.setContent {
            MobileApp(
                repository,
                RefreshFeedIfNeeded(repository, FeedClock { 10 }),
                true,
                { "14 October 2026" },
                FakeAuthRepository(),
            )
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Изображение недоступно").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("feed.article.missing").assertIsDisplayed()
        compose.onNodeWithText("Без заголовка").assertIsDisplayed()
        compose.onNodeWithText("Изображение недоступно").assertIsDisplayed()
    }

    @Test
    fun selectedCardCompositionRendersThroughProductionImageLoader() {
        val directory = InstrumentationRegistry.getArguments().getString("feedVisualFixtures")
        assumeTrue("Optional visual assets must be supplied outside the APK", directory != null)
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        val titles = listOf(
            "New tools help cities map urban heat",
            "Researchers test solar-powered sensors",
            "Public libraries expand digital access",
        )
        val sources = listOf("Demo Cities", "Demo Science", "Demo Community")
        val photos = listOf("city", "sensor", "library")
        val repository = FakeScreenRepository(
            articles = photos.mapIndexed { index, photo ->
                Article(
                    "visual-$index",
                    title = titles[index],
                    sourceName = sources[index],
                    publishedAtEpochMilliseconds = 1_791_936_000_000L - index * 86_400_000L,
                    imageUrl = File(directory, "$photo.png").toURI().toString(),
                )
            },
        )
        compose.setContent {
            MobileApp(
                repository,
                RefreshFeedIfNeeded(repository, FeedClock { 10 }),
                true,
                {
                    java.text.DateFormat.getDateInstance(
                        java.text.DateFormat.MEDIUM,
                        java.util.Locale.forLanguageTag(
                            InstrumentationRegistry.getArguments().getString("feedVisualLocale") ?: "ru",
                        ),
                    ).format(java.util.Date(it))
                },
                FakeAuthRepository(),
                colorScheme = androidColorScheme(
                    useDynamicColor = InstrumentationRegistry.getArguments().getString("feedVisualDynamic") == "true",
                ),
            )
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Загрузка новостей").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText(titles.first()).assertIsDisplayed()
        compose.onNodeWithText("Изображение недоступно").assertDoesNotExist()

        capture("android-reference-content")
    }

    @Test
    fun wideGridUsesMultipleColumnsAndGrowsForLongTitles() {
        val repository = FakeScreenRepository(
            articles = listOf(
                Article(
                    "first",
                    title = "A long title that must wrap naturally and remain fully readable at larger sizes",
                ),
                Article("second", title = "Second article"),
                Article("missing"),
            ),
        )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MobileApp(
                    repository,
                    RefreshFeedIfNeeded(repository, FeedClock { 10 }),
                    true,
                    { "14 October 2026" },
                    FakeAuthRepository(),
                    colorScheme = lightColorScheme(),
                )
            }
        }

        val first = compose.onNodeWithTag("feed.article.first").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("feed.article.second").fetchSemanticsNode().boundsInRoot

        assertEquals(first.top, second.top, 1f)
        assertTrue(second.left > first.left)
        assertTrue(first.width >= 320f)
        assertTrue(first.height > second.height)
        compose.onNodeWithText("Без заголовка").assertIsDisplayed()
        capture("android-wide-grid-large-text")
    }

    @Test
    fun refreshFailureRetainsCardsAndRetryPublishesUpdatedContent() {
        val repository = FakeScreenRepository(refreshFailsOnce = true)
        compose.setContent {
            MobileApp(
                repository,
                RefreshFeedIfNeeded(repository, FeedClock { 10 }),
                true,
                { "14 October 2026" },
                FakeAuthRepository(),
            )
        }

        compose.onNodeWithTag("feed.list").performTouchInput { swipeDown() }
        compose.waitUntil(5_000) { repository.refreshes == 1 }
        compose.onNodeWithTag("feed.list").performScrollToIndex(10)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("feed.retryButton").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("feed.retryButton").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { repository.refreshes == 2 }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("feed.retryButton").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("feed.list").performScrollToIndex(0)

        compose.waitUntil(5_000) { compose.onAllNodesWithText("Refreshed article").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Refreshed article").assertIsDisplayed()
        compose.onNodeWithTag("feed.retryButton").assertDoesNotExist()
    }

    @Test
    fun missingConfigurationExplainsUnavailableRefreshInsteadOfShowingLoading() {
        val repository = FakeScreenRepository(articles = emptyList())
        compose.setContent {
            MobileApp(
                repository,
                RefreshFeedIfNeeded(repository, FeedClock { 10 }),
                false,
                { "30.09.2026" },
                FakeAuthRepository(),
            )
        }

        compose.onNodeWithText("Обновление недоступно: не настроен ключ API").assertIsDisplayed()
        compose.onNodeWithContentDescription("Загрузка новостей").assertDoesNotExist()
        compose.onNodeWithTag("feed.retryButton").assertDoesNotExist()
        assertEquals(0, repository.refreshes)
    }

    @Test
    fun cardsOmitDescriptionsAndScrollSurvivesRestoration() {
        val repository = FakeScreenRepository(
            articles = (0..29).map {
                Article("fixture-$it", title = "News $it", summary = "Summary $it")
            },
        )
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MobileApp(
                repository,
                RefreshFeedIfNeeded(
                    repository,
                    FeedClock {
                        10
                    },
                ),
                true,
                { "30.09.2026" },
                FakeAuthRepository(),
            )
        }
        compose.onNodeWithText("News 0").assertIsDisplayed()
        compose.waitForIdle()
        capture("android-card")
        compose.onNodeWithText("Summary 0").assertDoesNotExist()
        compose.onNodeWithText("Избранное").performClick()
        compose.onNodeWithText("Раздел в разработке").assertIsDisplayed()
        compose.onNodeWithText("Профиль").performClick()
        compose.onNodeWithText("Добро пожаловать").assertIsDisplayed()
        compose.onNodeWithText("Новости").performClick()
        compose.onNodeWithText("Summary 0").assertDoesNotExist()
        compose.onNodeWithTag("feed.list").performScrollToIndex(5)
        compose.onNodeWithText("News 5").assertIsDisplayed()
        compose.onNodeWithText("News 0").assertIsNotDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("News 5").assertIsDisplayed()
        compose.onNodeWithText("News 0").assertIsNotDisplayed()

        compose.onNodeWithText("Профиль").performClick()
        compose.onNodeWithText("Добро пожаловать").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("News 5").assertIsDisplayed()
        compose.onNodeWithText("News 0").assertIsNotDisplayed()
    }

    @Test
    fun paginationFailureRequiresRetryAndPreservesCards() {
        val repository = FakeScreenRepository(hasMore = true)
        compose.setContent {
            MobileApp(
                repository,
                RefreshFeedIfNeeded(
                    repository,
                    FeedClock {
                        10
                    },
                ),
                true,
                { "30.09.2026" },
                FakeAuthRepository(),
            )
        }
        compose.onNodeWithTag("feed.list").performScrollToIndex(9)
        compose.waitUntil(5_000) { repository.appends == 1 }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("feed.retryButton").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("feed.list").performScrollToIndex(10)
        capture("android-append-error")
        compose.onNodeWithTag("feed.retryButton").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { repository.appends == 2 }
        compose.waitUntil(5_000) { repository.articles.any { it.id == "appended" } }
        compose.waitForIdle()
        compose.onNodeWithTag("feed.list").performScrollToIndex(10)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Appended article").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Appended article").assertIsDisplayed()
        compose.onNodeWithTag("feed.retryButton").assertDoesNotExist()
        compose.waitForIdle()
        assertEquals(2, repository.appends)
    }

    @Test
    fun darkNarrowScreenSupportsLargeText() {
        val repository = FakeScreenRepository()
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = Configuration.UI_MODE_NIGHT_YES
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, 2f),
            ) {
                Box(Modifier.width(320.dp)) {
                    MobileApp(
                        repository,
                        RefreshFeedIfNeeded(
                            repository,
                            FeedClock {
                                10
                            },
                        ),
                        true,
                        { "30.09.2026" },
                        FakeAuthRepository(),
                    )
                }
            }
        }
        compose.onNodeWithText("News 0").assertIsDisplayed()
        compose.onNodeWithText("Summary 0").assertDoesNotExist()
        compose.onNodeWithText("Избранное").assertIsDisplayed()
        compose.onNodeWithText("Профиль").assertIsDisplayed()
        capture("android-dark-large-text")
    }

    @Test
    fun pullToRefreshRequestsPageOne() {
        val repository = FakeScreenRepository()
        compose.setContent {
            MobileApp(
                repository,
                RefreshFeedIfNeeded(
                    repository,
                    FeedClock {
                        10
                    },
                ),
                true,
                { "30.09.2026" },
                FakeAuthRepository(),
            )
        }
        compose.onNodeWithText("News 0").assertIsDisplayed()
        compose.onNodeWithTag("feed.list").performTouchInput { swipeDown() }
        compose.waitUntil(5_000) { repository.refreshes == 1 }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Refreshed article").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Refreshed article").assertIsDisplayed()
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

private class FakeScreenRepository(
    hasMore: Boolean = false,
    articles: List<Article> = (0..9).map { Article(id = "fixture-$it", title = "News $it", summary = "Summary $it") },
    private val refreshFailsOnce: Boolean = false,
) : NewsRepository {
    private val snapshots = MutableStateFlow<FeedReadResult>(
        FeedReadResult.Snapshot(
            FeedSnapshot(
                articles,
                1,
                hasMore,
                false,
            ),
        ),
    )

    @Volatile var appends = 0

    @Volatile var refreshes = 0

    val articles: List<Article> get() = (snapshots.value as FeedReadResult.Snapshot).feed.articles

    override fun observeFeed() = snapshots

    override suspend fun refresh(): FeedUpdateResult {
        refreshes++
        delay(50)
        if (refreshFailsOnce && refreshes == 1) return FeedUpdateResult.Failed(FeedFailure.NETWORK)

        val current = (snapshots.value as FeedReadResult.Snapshot).feed
        snapshots.value = FeedReadResult.Snapshot(
            current.copy(
                articles = listOf(Article("refreshed", title = "Refreshed article")) + current.articles.drop(1),
            ),
        )

        return FeedUpdateResult.Updated
    }

    override suspend fun loadNextPage(): FeedUpdateResult {
        appends++
        delay(50)
        if (appends > 1) {
            val current = (snapshots.value as FeedReadResult.Snapshot).feed
            snapshots.value = FeedReadResult.Snapshot(
                current.copy(
                    articles = current.articles + Article("appended", title = "Appended article"),
                    hasMore = false,
                ),
            )
            return FeedUpdateResult.Updated
        }

        return FeedUpdateResult.Failed(FeedFailure.NETWORK)
    }
}
