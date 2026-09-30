package com.ndynagn.kmp.news.feature.feed.presentation

import com.ndynagn.kmp.news.feature.feed.domain.FeedFailure
import com.ndynagn.kmp.news.feature.feed.domain.FeedSnapshot

internal enum class FeedOperation { ACTIVATE, REFRESH, APPEND }

internal data class FeedProblem(val operation: FeedOperation, val failure: FeedFailure)

/** Request and recovery information that can coexist with cached content. */
internal data class FeedStatus(
    val operation: FeedOperation? = null,
    val problem: FeedProblem? = null,
    val storageFailed: Boolean = false,
    val isConfigured: Boolean = true,
)

internal sealed interface FeedUiState {
    val status: FeedStatus
    val snapshot: FeedSnapshot? get() = null

    data class Initial(override val status: FeedStatus) : FeedUiState
    data class Loading(override val status: FeedStatus) : FeedUiState
    data class Empty(override val status: FeedStatus) : FeedUiState
    data class Error(override val status: FeedStatus) : FeedUiState
    data class Content(override val snapshot: FeedSnapshot, override val status: FeedStatus) : FeedUiState

    val canAppend: Boolean
        get() = this is Content && status.isConfigured && !status.storageFailed &&
            status.operation == null && status.problem == null && snapshot.hasMore && !snapshot.isCacheLimitReached
}
