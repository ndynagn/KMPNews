package com.ndynagn.kmp.news.feature.feed.domain

/**
 * Persisted cards ordered newest first, unknown dates last, with stable publication-time ties.
 *
 * [lastRefreshedAtEpochMilliseconds] is Unix epoch milliseconds from the last successful
 * first-page update; null means never initialized, while empty success initializes it.
 * [hasMore] means a server cursor exists, not that append is currently permitted.
 * [isCacheLimitReached] reports local capacity independently; refresh remains available.
 * See [feed contract](../../../../../../../../../../../docs/news-feed-data-domain-plan.md), Cache and pagination.
 */
data class FeedSnapshot(
    val articles: List<Article>,
    val lastRefreshedAtEpochMilliseconds: Long?,
    val hasMore: Boolean,
    val isCacheLimitReached: Boolean,
)
