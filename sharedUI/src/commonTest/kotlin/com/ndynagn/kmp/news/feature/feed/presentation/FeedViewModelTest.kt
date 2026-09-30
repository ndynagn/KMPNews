package com.ndynagn.kmp.news.feature.feed.presentation

import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.feature.feed.domain.FeedClock
import com.ndynagn.kmp.news.feature.feed.domain.FeedFailure
import com.ndynagn.kmp.news.feature.feed.domain.FeedReadResult
import com.ndynagn.kmp.news.feature.feed.domain.FeedSnapshot
import com.ndynagn.kmp.news.feature.feed.domain.FeedUpdateResult
import com.ndynagn.kmp.news.feature.feed.domain.NewsRepository
import com.ndynagn.kmp.news.feature.feed.domain.RefreshFeedIfNeeded
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @Test
    fun activationReadsCacheAndCancelsObserversOnExit() = runTest {
        val repository = FakeNewsRepository()
        val model = model(repository)
        val session = launch { model.activate() }

        runCurrent()

        assertEquals("one", model.state.value.snapshot?.articles?.single()?.id)
        assertEquals(0, repository.refreshes)
        assertEquals(1, repository.observers)

        session.cancelAndJoin()

        assertEquals(0, repository.observers)

        val next = launch { model.activate() }

        runCurrent()

        assertEquals(1, repository.observers)

        next.cancelAndJoin()
    }

    @Test
    fun appendFailureRetainsCardsAndWaitsForExplicitRetry() = runTest {
        val repository = FakeNewsRepository()
        val model = model(repository)
        val session = launch { model.activate() }

        runCurrent()
        repository.result = FeedUpdateResult.Failed(FeedFailure.NETWORK)
        model.onEvent(FeedEvent.LOAD_MORE)
        runCurrent()

        assertEquals(1, model.state.value.snapshot?.articles?.size)
        assertNotNull(model.state.value.status.problem)

        session.cancelAndJoin()

        val reactivated = launch { model.activate() }

        runCurrent()

        assertNotNull(model.state.value.status.problem)

        model.onEvent(FeedEvent.LOAD_MORE)
        runCurrent()

        assertEquals(1, repository.appends)

        repository.result = FeedUpdateResult.Updated
        model.onEvent(FeedEvent.RETRY)
        runCurrent()

        assertEquals(2, repository.appends)
        assertNull(model.state.value.status.problem)

        reactivated.cancelAndJoin()
    }

    @Test
    fun refreshBypassesFreshnessAndOverlappingActionsDoNotDuplicateRequests() = runTest {
        val repository = FakeNewsRepository()
        val model = model(repository)
        val session = launch { model.activate() }

        runCurrent()
        repository.gate = CompletableDeferred()
        model.onEvent(FeedEvent.REFRESH)
        model.onEvent(FeedEvent.REFRESH)
        model.onEvent(FeedEvent.LOAD_MORE)
        runCurrent()

        assertEquals(1, repository.refreshes)
        assertEquals(0, repository.appends)
        assertEquals(FeedOperation.REFRESH, model.state.value.status.operation)

        session.cancelAndJoin()

        assertEquals(0, repository.activeRequests)
        assertNull(model.state.value.status.operation)
    }

    @Test
    fun storageRetryResubscribesAndMissingKeyDoesNotRequestNetwork() = runTest {
        val repository = FakeNewsRepository()
        repository.read.value = FeedReadResult.StorageFailure

        val model = model(repository, configured = false)
        val session = launch { model.activate() }

        runCurrent()

        assertTrue(model.state.value.status.storageFailed)

        repository.read.value = snapshot()
        model.onEvent(FeedEvent.RETRY)
        runCurrent()

        assertFalse(model.state.value.status.storageFailed)

        model.onEvent(FeedEvent.REFRESH)
        runCurrent()

        assertEquals(0, repository.refreshes)
        assertFalse(model.state.value.canAppend)

        session.cancelAndJoin()
    }

    @Test
    fun initialEmptyCacheRefreshesButInitializedEmptyAndLimitsDoNotAppend() = runTest {
        val repository = FakeNewsRepository()
        repository.read.value = FeedReadResult.Snapshot(FeedSnapshot(emptyList(), null, false, false))

        val model = model(repository)
        val session = launch { model.activate() }

        runCurrent()

        assertEquals(1, repository.refreshes)

        session.cancelAndJoin()

        repository.read.value = FeedReadResult.Snapshot(FeedSnapshot(emptyList(), 1, false, false))

        val local = model(repository, configured = false)
        val localSession = launch { local.activate() }

        runCurrent()

        assertTrue(local.state.value is FeedUiState.Empty)
        assertFalse(local.state.value.canAppend)

        localSession.cancelAndJoin()
    }

    @Test
    fun contentSurvivesRefreshAndStorageFailureAndCapacityStopsAppend() = runTest {
        val repository = FakeNewsRepository()
        val model = model(repository)

        assertTrue(model.state.value is FeedUiState.Initial)

        val session = launch { model.activate() }

        runCurrent()

        assertTrue(model.state.value is FeedUiState.Content)

        repository.result = FeedUpdateResult.Failed(FeedFailure.NETWORK)
        model.onEvent(FeedEvent.REFRESH)
        runCurrent()

        assertTrue(model.state.value is FeedUiState.Content)
        assertNotNull(model.state.value.status.problem)
        assertEquals("one", model.state.value.snapshot?.articles?.single()?.id)

        repository.read.value = FeedReadResult.StorageFailure
        runCurrent()

        assertTrue(model.state.value is FeedUiState.Content)
        assertTrue(model.state.value.status.storageFailed)

        repository.result = FeedUpdateResult.Updated
        repository.read.value = snapshot()
        model.onEvent(FeedEvent.RETRY)
        runCurrent()

        assertNull(model.state.value.status.problem)

        repository.read.value = FeedReadResult.Snapshot(FeedSnapshot(listOf(Article("one")), 1, false, false))
        runCurrent()
        model.onEvent(FeedEvent.LOAD_MORE)

        assertEquals(0, repository.appends)

        repository.read.value = FeedReadResult.Snapshot(FeedSnapshot(listOf(Article("one")), 1, true, true))
        runCurrent()
        model.onEvent(FeedEvent.LOAD_MORE)

        assertEquals(0, repository.appends)

        session.cancelAndJoin()
    }

    @Test
    fun emptyRefreshFailureIsErrorAndRetryReturnsEmpty() = runTest {
        val repository = FakeNewsRepository()
        repository.read.value = FeedReadResult.Snapshot(FeedSnapshot(emptyList(), null, false, false))
        repository.gate = CompletableDeferred()

        val model = model(repository)
        val session = launch { model.activate() }

        runCurrent()

        assertTrue(model.state.value is FeedUiState.Loading)

        repository.result = FeedUpdateResult.Failed(FeedFailure.NETWORK)
        repository.gate?.complete(Unit)
        runCurrent()

        assertTrue(model.state.value is FeedUiState.Error)

        repository.result = FeedUpdateResult.Updated
        model.onEvent(FeedEvent.RETRY)
        runCurrent()

        assertTrue(model.state.value is FeedUiState.Empty)

        session.cancelAndJoin()
    }

    @Test
    fun cancelledRequestCannotPublishLateFailureAndReactivationWaits() = runTest {
        val repository = FakeNewsRepository()
        val model = model(repository)
        val session = launch { model.activate() }

        runCurrent()
        repository.gate = CompletableDeferred()
        repository.ignoreCancellation = true
        repository.result = FeedUpdateResult.Failed(FeedFailure.NETWORK)
        model.onEvent(FeedEvent.REFRESH)
        runCurrent()
        session.cancel()

        val next = launch { model.activate() }

        runCurrent()

        assertEquals(1, repository.activeRequests)

        repository.gate?.complete(Unit)
        runCurrent()

        assertNull(model.state.value.status.problem)
        assertEquals(1, repository.refreshes)
        assertEquals(1, repository.observers)

        next.cancelAndJoin()
    }

    private fun model(repository: FakeNewsRepository, configured: Boolean = true) = FeedViewModel(
        repository,
        RefreshFeedIfNeeded(repository, FeedClock { 10 }),
        configured,
    )
}

private fun snapshot() = FeedReadResult.Snapshot(FeedSnapshot(listOf(Article("one")), 1, true, false))

private class FakeNewsRepository : NewsRepository {
    val read = MutableStateFlow<FeedReadResult>(snapshot())
    var result: FeedUpdateResult = FeedUpdateResult.Updated
    var gate: CompletableDeferred<Unit>? = null
    var observers = 0
    var refreshes = 0
    var appends = 0
    var activeRequests = 0
    var ignoreCancellation = false

    override fun observeFeed(): Flow<FeedReadResult> = flow {
        observers++
        try {
            if (read.value is FeedReadResult.StorageFailure) emit(read.value) else emitAll(read)
        } finally {
            observers--
        }
    }

    override suspend fun refresh(): FeedUpdateResult {
        refreshes++
        return update()
    }

    override suspend fun loadNextPage(): FeedUpdateResult {
        appends++
        return update()
    }

    private suspend fun update(): FeedUpdateResult {
        activeRequests++
        try {
            if (ignoreCancellation) withContext(NonCancellable) { gate?.await() } else gate?.await()
            return result
        } finally {
            activeRequests--
        }
    }
}
