package com.ndynagn.kmp.news.feature.feed.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomFeedStore(private val feedDao: FeedDao) : FeedStore {
    override fun observe(): Flow<CachedFeed> = feedDao.observe().map { it.toCache() }

    override suspend fun read(): CachedFeed = feedDao.read().toCache()

    override suspend fun write(feed: CachedFeed) {
        feedDao.replace(
            feed.articles.mapIndexed { index, article -> article.toEntity(index) },
            FeedMetadataEntity(refreshedAt = requireNotNull(feed.refreshedAt), nextPage = feed.nextPage),
        )
    }

    private fun List<StoredFeed>.toCache(): CachedFeed {
        val stored = firstOrNull() ?: return CachedFeed()

        return CachedFeed(
            stored.articles.sortedBy { it.position }.map { it.toDomain() },
            stored.metadata.refreshedAt,
            stored.metadata.nextPage,
        )
    }
}
