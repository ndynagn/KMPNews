package com.ndynagn.kmp.news.feature.feed.data.remote

import com.ndynagn.kmp.news.data.news.MediatedNewsClient
import com.ndynagn.kmp.news.data.news.NewsRequestFailure
import com.ndynagn.kmp.news.data.news.NewsResponse
import com.ndynagn.kmp.news.feature.feed.di.FeedApiConfiguration
import com.ndynagn.kmp.news.feature.feed.domain.FeedFailure
import io.ktor.client.HttpClient

internal class NewsFeedClient(httpClient: HttpClient, configuration: FeedApiConfiguration) : NewsRemoteSource {
    private val client = MediatedNewsClient(httpClient, configuration.news)

    override suspend fun fetch(page: String?): PageResult = when (val result = client.fetch(null, page)) {
        is NewsResponse.Success -> PageResult.Success(NewsPage(result.page.articles, result.page.nextPage))
        is NewsResponse.Failure -> PageResult.Failure(result.failure.toFeedFailure())
    }

    private fun NewsRequestFailure.toFeedFailure(): FeedFailure = when (this) {
        NewsRequestFailure.NETWORK -> FeedFailure.NETWORK
        NewsRequestFailure.TIMEOUT -> FeedFailure.TIMEOUT
        NewsRequestFailure.ACCESS_DENIED -> FeedFailure.ACCESS_DENIED
        NewsRequestFailure.INVALID_REQUEST -> FeedFailure.INVALID_REQUEST
        NewsRequestFailure.RATE_LIMITED -> FeedFailure.RATE_LIMITED
        NewsRequestFailure.QUOTA_EXCEEDED -> FeedFailure.QUOTA_EXCEEDED
        NewsRequestFailure.SERVER_UNAVAILABLE -> FeedFailure.SERVER_UNAVAILABLE
        NewsRequestFailure.INVALID_RESPONSE -> FeedFailure.INVALID_RESPONSE
    }
}
