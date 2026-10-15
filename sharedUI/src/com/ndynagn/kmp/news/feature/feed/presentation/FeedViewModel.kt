package com.ndynagn.kmp.news.feature.feed.presentation

import androidx.lifecycle.ViewModel
import com.ndynagn.kmp.news.feature.feed.domain.FeedFailure
import com.ndynagn.kmp.news.feature.feed.domain.FeedReadResult
import com.ndynagn.kmp.news.feature.feed.domain.FeedSnapshot
import com.ndynagn.kmp.news.feature.feed.domain.FeedUpdateResult
import com.ndynagn.kmp.news.feature.feed.domain.NewsRepository
import com.ndynagn.kmp.news.feature.feed.domain.RefreshFeedIfNeeded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FeedViewModel(
    private val newsRepository: NewsRepository,
    private val refreshFeedIfNeeded: RefreshFeedIfNeeded,
    isConfigured: Boolean,
) : ViewModel() {
    private var status = FeedStatus(isConfigured = isConfigured)
    private var snapshot: FeedSnapshot? = null
    private val mutableState = MutableStateFlow<FeedUiState>(FeedUiState.Initial(status))
    val state = mutableState.asStateFlow()
    private val sessionMutex = Mutex()
    private var activeScope: CoroutineScope? = null
    private var observer: Job? = null
    private var request: Job? = null

    /** The visible route owns this structured session; cancellation stops all of its children. */
    suspend fun activate(): Unit = sessionMutex.withLock { runSession() }

    private suspend fun runSession(): Unit = coroutineScope {
        if (activeScope != null) return@coroutineScope

        activeScope = this
        observe()
        start(FeedOperation.ACTIVATE)

        try {
            awaitCancellation()
        } finally {
            activeScope = null
            observer = null
            request = null
            status = status.copy(operation = null)
            publishState()
        }
    }

    fun onEvent(event: FeedEvent) {
        if (activeScope == null) return

        when (event) {
            FeedEvent.REFRESH -> start(FeedOperation.REFRESH)

            FeedEvent.LOAD_MORE -> if (state.value.canAppend) start(FeedOperation.APPEND)

            FeedEvent.RETRY -> {
                if (request?.isActive == true) return

                val operation = status.problem?.operation ?: FeedOperation.ACTIVATE

                if (status.storageFailed) observe()

                status = status.copy(problem = null)
                publishState()
                start(operation)
            }
        }
    }

    private fun observe() {
        observer?.cancel()

        observer = activeScope?.launch {
            newsRepository.observeFeed().collect { result ->
                coroutineContext.ensureActive()

                when (result) {
                    is FeedReadResult.Snapshot -> {
                        snapshot = result.feed
                        status = status.copy(storageFailed = false)
                    }

                    FeedReadResult.StorageFailure -> status = status.copy(storageFailed = true)
                }

                publishState()
            }
        }
    }

    private fun start(operation: FeedOperation) {
        val scope = activeScope ?: return

        if (request?.isActive == true || !status.isConfigured) return

        val retainedProblem = if (operation == FeedOperation.ACTIVATE) status.problem else null

        status = status.copy(operation = operation, problem = retainedProblem)
        publishState()

        request = scope.launch {
            val result = when (operation) {
                FeedOperation.ACTIVATE -> refreshFeedIfNeeded()
                FeedOperation.REFRESH -> newsRepository.refresh()
                FeedOperation.APPEND -> newsRepository.loadNextPage()
            }

            coroutineContext.ensureActive()

            val failure = (result as? FeedUpdateResult.Failed)?.failure

            status = status.copy(
                operation = null,
                problem = if (operation == FeedOperation.ACTIVATE && status.problem != null) {
                    status.problem
                } else {
                    failure?.let { FeedProblem(operation, it) }
                },
                storageFailed = status.storageFailed || failure == FeedFailure.STORAGE,
            )
            publishState()
        }
    }

    private fun publishState() {
        val cached = snapshot

        mutableState.value = when {
            cached != null && cached.articles.isNotEmpty() -> FeedUiState.Content(cached, status)
            status.storageFailed || status.problem != null -> FeedUiState.Error(status)
            status.operation != null || cached == null -> FeedUiState.Loading(status)
            else -> FeedUiState.Empty(status)
        }
    }
}
