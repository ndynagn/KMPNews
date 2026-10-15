package com.ndynagn.kmp.news.feature.search.data

import com.ndynagn.kmp.news.data.news.MediatedNewsClient
import com.ndynagn.kmp.news.data.news.NewsRequestFailure
import com.ndynagn.kmp.news.data.news.NewsResponse
import com.ndynagn.kmp.news.feature.search.domain.SearchFailure
import com.ndynagn.kmp.news.feature.search.domain.SearchQuery
import com.ndynagn.kmp.news.feature.search.domain.SearchRepository
import com.ndynagn.kmp.news.feature.search.domain.SearchResult
import com.ndynagn.kmp.news.network.NewsApiConfiguration

internal class RemoteSearchRepository(
    private val client: MediatedNewsClient,
    private val configuration: NewsApiConfiguration,
) : SearchRepository {
    override suspend fun search(query: String, cursor: String?): SearchResult {
        val normalized = SearchQuery.normalize(query) ?: return SearchResult.Failed(SearchFailure.INVALID_QUERY)
        if (!configuration.isConfigured) return SearchResult.Failed(SearchFailure.NOT_CONFIGURED)

        return when (val result = client.fetch(normalized, cursor)) {
            is NewsResponse.Success -> SearchResult.Page(result.page.articles, result.page.nextPage)
            is NewsResponse.Failure -> SearchResult.Failed(result.failure.toSearchFailure())
        }
    }

    private fun NewsRequestFailure.toSearchFailure(): SearchFailure = when (this) {
        NewsRequestFailure.NETWORK -> SearchFailure.NETWORK
        NewsRequestFailure.TIMEOUT -> SearchFailure.TIMEOUT
        NewsRequestFailure.ACCESS_DENIED -> SearchFailure.ACCESS_DENIED
        NewsRequestFailure.INVALID_REQUEST -> SearchFailure.INVALID_QUERY
        NewsRequestFailure.RATE_LIMITED -> SearchFailure.RATE_LIMITED
        NewsRequestFailure.QUOTA_EXCEEDED -> SearchFailure.QUOTA_EXCEEDED
        NewsRequestFailure.SERVER_UNAVAILABLE -> SearchFailure.SERVICE
        NewsRequestFailure.INVALID_RESPONSE -> SearchFailure.INVALID_RESPONSE
    }
}
