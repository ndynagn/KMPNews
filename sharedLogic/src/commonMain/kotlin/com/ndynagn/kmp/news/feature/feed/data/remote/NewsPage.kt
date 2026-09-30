package com.ndynagn.kmp.news.feature.feed.data.remote

import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.feature.feed.domain.FeedFailure

internal data class NewsPage(val articles: List<Article>, val nextPage: String?)

internal sealed interface PageResult {
    data class Success(val page: NewsPage) : PageResult

    data class Failure(val failure: FeedFailure) : PageResult
}

internal fun interface NewsRemoteSource {
    suspend fun fetch(page: String?): PageResult
}
