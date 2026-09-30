package com.ndynagn.kmp.news.feature.feed

import com.ndynagn.kmp.news.feature.feed.data.OfflineFirstNewsRepository
import com.ndynagn.kmp.news.feature.feed.data.local.CachedFeed
import com.ndynagn.kmp.news.feature.feed.data.local.FeedStore
import com.ndynagn.kmp.news.feature.feed.data.remote.NewsPage
import com.ndynagn.kmp.news.feature.feed.data.remote.NewsRemoteSource
import com.ndynagn.kmp.news.feature.feed.data.remote.PageResult
import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.feature.feed.domain.FeedClock
import com.ndynagn.kmp.news.feature.feed.domain.FeedFailure
import com.ndynagn.kmp.news.feature.feed.domain.FeedReadResult
import com.ndynagn.kmp.news.feature.feed.domain.FeedUpdateResult
import com.ndynagn.kmp.news.feature.feed.domain.RefreshFeedIfNeeded
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FeedRepositoryTest {
    @Test
    fun observationNeverFetchesAndUninitializedAppendLoadsFirstPage() = runTest {
        val fixture = Fixture()

        val initial = assertIs<FeedReadResult.Snapshot>(fixture.repository.observeFeed().first())

        assertEquals(null, initial.feed.lastRefreshedAtEpochMilliseconds)
        assertTrue(fixture.requests.isEmpty())
        assertEquals(FeedUpdateResult.Updated, fixture.repository.loadNextPage())
        assertEquals(listOf<String?>(null), fixture.requests.toList())
    }

    @Test
    fun refreshMergesUpdatesAndPreservesStableTiesAndNullDates() = runTest {
        val fixture =
            Fixture(CachedFeed(listOf(article("old", 10), article("tie", 10), article("unknown", null)), 1, "old"))
        fixture.page =
            NewsPage(
                listOf(article("new", 20), article("old", 10).copy(title = "updated"), article("newTie", 10)),
                "new",
            )

        fixture.repository.refresh()

        assertEquals(listOf("new", "old", "tie", "newTie", "unknown"), fixture.store.value.articles.map { it.id })
        assertEquals("updated", fixture.store.value.articles[1].title)
        assertEquals("new", fixture.store.value.nextPage)
    }

    @Test
    fun appendPreservesFreshnessAndPassesOpaqueCursor() = runTest {
        val fixture = Fixture(CachedFeed(listOf(article("one", 1)), 42, "opaque+/="))
        fixture.page = NewsPage(listOf(article("one", 1), article("two", 0)), null)

        fixture.repository.loadNextPage()

        assertEquals(listOf<String?>("opaque+/="), fixture.requests.toList())
        assertEquals(42L, fixture.store.value.refreshedAt)
        assertEquals(2, fixture.store.value.articles.size)
        assertEquals(FeedUpdateResult.EndReached, fixture.repository.loadNextPage())
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun capacityIsDistinctFromExhaustionAndRefreshStillWorks() = runTest {
        val fixture = Fixture(CachedFeed((0..199).map { article("$it", it.toLong()) }, 42, "next"))

        assertEquals(FeedUpdateResult.CacheLimitReached, fixture.repository.loadNextPage())
        assertTrue(fixture.requests.isEmpty())

        fixture.page = NewsPage(listOf(article("latest", 999)), "reset")

        assertEquals(FeedUpdateResult.Updated, fixture.repository.refresh())
        assertEquals(200, fixture.store.value.articles.size)
        assertEquals("latest", fixture.store.value.articles.first().id)
        assertTrue(fixture.store.value.articles.none { it.id == "0" })
    }

    @Test
    fun appendTrimsOverflowAndDeduplicatesWithinPage() = runTest {
        val fixture = Fixture(CachedFeed((0..197).map { article("$it", it.toLong()) }, 42, "next"))
        fixture.page =
            NewsPage(
                listOf(article("x", 999), article("x", 999).copy(title = "last"), article("y", 998), article("z", 997)),
                "more",
            )

        fixture.repository.loadNextPage()

        assertEquals(200, fixture.store.value.articles.size)
        assertEquals("last", fixture.store.value.articles.first().title)
    }

    @Test
    fun emptyRefreshRetainsCardsButInitializesFreshnessAndCursor() = runTest {
        val fixture = Fixture(CachedFeed(listOf(article("old", 1)), 1, "old"))
        fixture.page = NewsPage(emptyList(), null)

        fixture.repository.refresh()

        assertEquals(listOf("old"), fixture.store.value.articles.map { it.id })
        assertEquals(fixture.now, fixture.store.value.refreshedAt)
        assertEquals(null, fixture.store.value.nextPage)
        assertEquals(FeedUpdateResult.Fresh, RefreshFeedIfNeeded(fixture.repository, fixture.clock)())
    }

    @Test
    fun successfulEmptyInitialRefreshIsNotRepeatedUntilStale() = runTest {
        val fixture = Fixture()
        val refresh = RefreshFeedIfNeeded(fixture.repository, fixture.clock)

        assertEquals(FeedUpdateResult.Updated, refresh())
        assertEquals(FeedUpdateResult.Fresh, refresh())
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun freshnessBoundaryAndBackwardsClockAreExplicit() = runTest {
        val fixture = Fixture(CachedFeed(refreshedAt = 10_000))
        val refresh = RefreshFeedIfNeeded(fixture.repository, fixture.clock)

        fixture.now = 10_000 + 3_600_000 - 1

        assertEquals(FeedUpdateResult.Fresh, refresh())

        fixture.now++

        assertEquals(FeedUpdateResult.Updated, refresh())

        fixture.now = 0

        assertEquals(FeedUpdateResult.Updated, refresh())
    }

    @Test
    fun failedNetworkOrWritePreservesEntireSnapshot() = runTest {
        val initial = CachedFeed(listOf(article("old", 1)), 123, "cursor")
        val fixture = Fixture(initial)
        fixture.failure = FeedFailure.QUOTA_EXCEEDED

        assertEquals(FeedUpdateResult.Failed(FeedFailure.QUOTA_EXCEEDED), fixture.repository.refresh())
        assertEquals(initial, fixture.store.value)

        fixture.failure = null
        fixture.store.failWrite = true

        assertEquals(FeedUpdateResult.Failed(FeedFailure.STORAGE), fixture.repository.loadNextPage())
        assertEquals(initial, fixture.store.value)
    }

    @Test
    fun readFailureIsAValueAndNeverRequestsNetwork() = runTest {
        val fixture = Fixture()
        fixture.store.failRead = true

        assertEquals(listOf(FeedReadResult.StorageFailure), fixture.repository.observeFeed().toList())
        assertEquals(FeedUpdateResult.Failed(FeedFailure.STORAGE), fixture.repository.refresh())
        assertEquals(
            FeedUpdateResult.Failed(FeedFailure.STORAGE),
            RefreshFeedIfNeeded(fixture.repository, fixture.clock)(),
        )
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun concurrentMutationIsRejectedAndCancellationReleasesGuard() = runTest {
        val fixture = Fixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.beforeFetch = {
            entered.complete(Unit)
            release.await()
        }

        val first = async { fixture.repository.refresh() }
        entered.await()

        assertEquals(FeedUpdateResult.AlreadyRunning, fixture.repository.loadNextPage())

        first.cancelAndJoin()

        assertTrue(first.isCancelled)
        assertEquals(null, fixture.store.value.refreshedAt)

        fixture.beforeFetch = {}

        assertEquals(FeedUpdateResult.Updated, fixture.repository.refresh())
    }

    private fun article(id: String, date: Long?): Article = Article(id, publishedAtEpochMilliseconds = date)

    private class Fixture(initial: CachedFeed = CachedFeed()) {
        val store = FakeFeedStore(initial)
        var now = 10_000L
        val clock = FeedClock { now }
        val requests = mutableListOf<String?>()
        var page = NewsPage(emptyList(), null)
        var failure: FeedFailure? = null
        var beforeFetch: suspend () -> Unit = {}
        val repository = OfflineFirstNewsRepository(
            store,
            NewsRemoteSource { cursor ->
                requests += cursor
                beforeFetch()
                failure?.let { PageResult.Failure(it) } ?: PageResult.Success(page)
            },
            clock,
        )
    }

    private class FakeFeedStore(initial: CachedFeed) : FeedStore {
        private val state = MutableStateFlow(initial)
        val value: CachedFeed get() = state.value
        var failWrite = false
        var failRead = false

        override fun observe() = flow {
            check(!failRead)

            state.collect { emit(it) }
        }

        override suspend fun read(): CachedFeed {
            check(!failRead)

            return value
        }

        override suspend fun write(feed: CachedFeed) {
            check(!failWrite)

            state.value = feed
        }
    }
}
