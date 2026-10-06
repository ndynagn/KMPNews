package com.ndynagn.kmp.news.data.news

import com.ndynagn.kmp.news.feature.feed.domain.Article

internal data class RemoteNewsPage(val articles: List<Article>, val nextPage: String?)

internal sealed interface NewsResponse {
    data class Success(val page: RemoteNewsPage) : NewsResponse
    data class Failure(val failure: NewsRequestFailure) : NewsResponse
}

internal enum class NewsRequestFailure {
    NETWORK,
    TIMEOUT,
    ACCESS_DENIED,
    INVALID_REQUEST,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    SERVER_UNAVAILABLE,
    INVALID_RESPONSE,
}
