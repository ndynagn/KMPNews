package com.ndynagn.kmp.news.feature.feed

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.ndynagn.kmp.news.feature.LocaleRule
import com.ndynagn.kmp.news.feature.RussianMobileApp
import com.ndynagn.kmp.news.feature.auth.FakeAuthRepository
import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.feature.feed.domain.FeedClock
import com.ndynagn.kmp.news.feature.feed.domain.FeedFailure
import com.ndynagn.kmp.news.feature.feed.domain.FeedReadResult
import com.ndynagn.kmp.news.feature.feed.domain.FeedSnapshot
import com.ndynagn.kmp.news.feature.feed.domain.FeedUpdateResult
import com.ndynagn.kmp.news.feature.feed.domain.NewsRepository
import com.ndynagn.kmp.news.feature.feed.domain.RefreshFeedIfNeeded
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FeedPaginationTest {
    @get:Rule(order = 0)
    val locale = LocaleRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun refreshAndPrependingPagesDoNotChainWithoutMoreScrolling() {
        val newsRepository = FakePaginationNewsRepository()

        showFeed(newsRepository)

        compose.onNodeWithTag("feed.list").performTouchInput { swipeDown() }
        compose.waitUntil(5_000) { newsRepository.refreshes == 1 }
        compose.waitForIdle()

        assertEquals(0, newsRepository.appends)

        compose.onNodeWithTag("feed.list").performScrollToIndex(newsRepository.articles.lastIndex)
        compose.waitForIdle()

        assertEquals(0, newsRepository.appends)

        scrollForward()
        completeAppend(newsRepository, expected = 1)

        assertEquals(1, newsRepository.appends)

        scrollForward()
        completeAppend(newsRepository, expected = 2)

        assertEquals(2, newsRepository.appends)
    }

    @Test
    fun emptyAndDuplicatePagesWaitForNewInputAndRestorationDoesNotAppend() {
        val newsRepository = FakePaginationNewsRepository()
        val restoration = StateRestorationTester(compose)

        showFeed(newsRepository, restoration)
        compose.onNodeWithTag("feed.list").performScrollToIndex(newsRepository.articles.lastIndex)

        for ((index, page) in listOf(emptyList(), newsRepository.articles.take(1)).withIndex()) {
            newsRepository.nextPage = page

            scrollForward()
            completeAppend(newsRepository, expected = index + 1)

            assertEquals(index + 1, newsRepository.appends)
            assertEquals(10, newsRepository.articles.size)
        }

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Профиль").performClick()
        compose.onNodeWithText("Новости").performClick()
        compose.onNodeWithTag("feed.article.pagination-9").performClick()

        compose.onNodeWithTag("article.screen").assertIsDisplayed()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()

        assertEquals(2, newsRepository.appends)
    }

    @Test
    fun busyInputIsNotQueuedAndAppendFailureRequiresExplicitRetry() {
        val newsRepository = FakePaginationNewsRepository()

        newsRepository.appendFails = true
        showFeed(newsRepository)
        compose.onNodeWithTag("feed.list").performScrollToIndex(newsRepository.articles.lastIndex)

        scrollForward()
        compose.waitUntil(5_000) { newsRepository.appends == 1 }
        scrollForward()
        completeAppend(newsRepository, expected = 1)
        scrollForward()

        assertEquals(1, newsRepository.appends)

        newsRepository.appendFails = false
        compose.onNodeWithTag("feed.retryButton").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { newsRepository.appends == 2 }
        scrollForward()
        completeAppend(newsRepository, expected = 2)

        assertEquals(2, newsRepository.appends)
        compose.onNodeWithTag("feed.retryButton").assertDoesNotExist()
    }

    @Test
    fun shortListLoadsOnlyWhenTheUserAttemptsForwardScrolling() {
        val newsRepository = FakePaginationNewsRepository(initialCount = 1)

        newsRepository.nextPage = emptyList()
        showFeed(newsRepository)
        compose.waitForIdle()

        assertEquals(0, newsRepository.appends)

        scrollForward()
        completeAppend(newsRepository, expected = 1)

        assertEquals(1, newsRepository.appends)

        scrollForward()
        completeAppend(newsRepository, expected = 2)

        assertEquals(2, newsRepository.appends)
    }

    private fun showFeed(newsRepository: FakePaginationNewsRepository, restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            RussianMobileApp(
                newsRepository = newsRepository,
                refreshFeedIfNeeded = RefreshFeedIfNeeded(newsRepository, FeedClock { 10L }),
                isConfigured = true,
                formatDate = { "23.10.2026" },
                authRepository = FakeAuthRepository(),
            )
        }

        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    private fun scrollForward() {
        compose.onNodeWithTag("feed.list").performTouchInput {
            down(center)
            moveTo(center.copy(y = center.y - height * 0.15f), delayMillis = 600)
            advanceEventTime(400)
            up()
        }
    }

    private fun completeAppend(newsRepository: FakePaginationNewsRepository, expected: Int) {
        compose.waitUntil(5_000) { newsRepository.appends == expected }
        newsRepository.appendGate.complete(Unit)
        compose.waitUntil(5_000) { newsRepository.completedAppends == expected }
        compose.waitForIdle()
    }
}

/** In-memory merge with controllable requests, including pages sorted ahead of the retained anchor. */
private class FakePaginationNewsRepository(initialCount: Int = 10) : NewsRepository {
    private val snapshots = MutableStateFlow<FeedReadResult>(
        FeedReadResult.Snapshot(
            FeedSnapshot(
                articles = (0 until initialCount).map { Article(id = "pagination-$it", title = "News $it") },
                lastRefreshedAtEpochMilliseconds = 1L,
                hasMore = true,
                isCacheLimitReached = false,
            ),
        ),
    )

    @Volatile var appends = 0

    @Volatile var completedAppends = 0

    @Volatile var refreshes = 0

    @Volatile var appendFails = false

    @Volatile var appendGate = CompletableDeferred<Unit>()

    @Volatile var nextPage: List<Article>? = null

    val articles: List<Article> get() = (snapshots.value as FeedReadResult.Snapshot).feed.articles

    override fun observeFeed() = snapshots

    override suspend fun refresh(): FeedUpdateResult {
        refreshes++
        prepend((0..9).map { Article(id = "refresh-$it", title = "Refreshed $it") })

        return FeedUpdateResult.Updated
    }

    override suspend fun loadNextPage(): FeedUpdateResult {
        appends++
        appendGate.await()
        appendGate = CompletableDeferred()

        val result = if (appendFails) {
            FeedUpdateResult.Failed(FeedFailure.NETWORK)
        } else {
            prepend(nextPage ?: (0..9).map { Article(id = "page-$appends-$it", title = "Page $appends news $it") })
            FeedUpdateResult.Updated
        }

        completedAppends++

        return result
    }

    private fun prepend(page: List<Article>) {
        val current = (snapshots.value as FeedReadResult.Snapshot).feed
        val merged = (page + current.articles).distinctBy { it.id }.take(200)

        snapshots.value =
            FeedReadResult.Snapshot(current.copy(articles = merged, isCacheLimitReached = merged.size == 200))
    }
}
