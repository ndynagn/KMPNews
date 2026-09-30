package com.ndynagn.kmp.news.feature.feed.data.local

import com.ndynagn.kmp.news.feature.feed.domain.Article
import kotlinx.coroutines.flow.Flow

internal data class CachedFeed(
    val articles: List<Article> = emptyList(),
    val refreshedAt: Long? = null,
    val nextPage: String? = null,
)

internal interface FeedStore {
    fun observe(): Flow<CachedFeed>

    suspend fun read(): CachedFeed

    suspend fun write(feed: CachedFeed)
}
