package com.ndynagn.kmp.news.feature.feed

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class FeedScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cardsOmitDescriptionsAndScrollSurvivesRestoration() {
        val repository = FakeScreenRepository()
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
        compose.onNodeWithText("News 5").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("News 5").assertIsDisplayed()
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
        compose.onNodeWithText("News 9").assertIsDisplayed()
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
        compose.onNodeWithText("News 0").assertIsDisplayed()
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

private class FakeScreenRepository(hasMore: Boolean = false) : NewsRepository {
    private val snapshots = MutableStateFlow<FeedReadResult>(
        FeedReadResult.Snapshot(
            FeedSnapshot(
                (0..9).map { Article(id = "fixture-$it", title = "News $it", summary = "Summary $it") },
                1,
                hasMore,
                false,
            ),
        ),
    )

    @Volatile var appends = 0

    @Volatile var refreshes = 0

    override fun observeFeed() = snapshots

    override suspend fun refresh(): FeedUpdateResult {
        refreshes++

        return FeedUpdateResult.Updated
    }

    override suspend fun loadNextPage(): FeedUpdateResult {
        appends++
        delay(50)

        return FeedUpdateResult.Failed(FeedFailure.NETWORK)
    }
}
