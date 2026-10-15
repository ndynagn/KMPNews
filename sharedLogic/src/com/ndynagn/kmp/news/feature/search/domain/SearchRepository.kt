package com.ndynagn.kmp.news.feature.search.domain

import com.ndynagn.kmp.news.feature.feed.domain.Article

/** Expected search failures; no storage failure exists for network-only search. */
enum class SearchFailure {
    NOT_CONFIGURED,
    INVALID_QUERY,
    NETWORK,
    TIMEOUT,
    ACCESS_DENIED,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    SERVICE,
    INVALID_RESPONSE,
}

/** Provider-ordered page with an opaque continuation, or a failure which must not replace existing results. */
sealed interface SearchResult {
    data class Page(val articles: List<Article>, val nextCursor: String?) : SearchResult
    data class Failed(val failure: SearchFailure) : SearchResult
}

/** Network-only search of latest English news through the mediator; owns no coroutine or persistent cache. */
interface SearchRepository {
    /**
     * Validates [query] before HTTP. [cursor] is the previous response's opaque cursor for the same query.
     * Expected errors are values. Caller cancellation propagates to HTTP and throws cancellation.
     */
    suspend fun search(query: String, cursor: String?): SearchResult
}
