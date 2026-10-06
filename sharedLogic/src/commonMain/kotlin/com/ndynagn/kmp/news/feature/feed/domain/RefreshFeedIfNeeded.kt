package com.ndynagn.kmp.news.feature.feed.domain

import kotlinx.coroutines.flow.first

/**
 * Refreshes on activation when uninitialized, at least one hour old, or after a backwards clock jump.
 *
 * Successful empty data is initialized. Observation failures are returned without a
 * network request. Cancellation propagates. Manual refresh calls [NewsRepository.refresh]
 * directly; rendering and background timers must not invoke this policy.
 * See [feed contract](../../../../../../../../../../../docs/contracts/news-feed.md), Refresh, concurrency and cancellation.
 */
class RefreshFeedIfNeeded(private val newsRepository: NewsRepository, private val clock: FeedClock) {
    /** Reads the local snapshot and either returns Fresh or delegates to the repository refresh. */
    suspend operator fun invoke(): FeedUpdateResult {
        val read = newsRepository.observeFeed().first()

        if (read is FeedReadResult.StorageFailure) {
            return FeedUpdateResult.Failed(FeedFailure.STORAGE)
        }

        val lastRefresh = (read as FeedReadResult.Snapshot).feed.lastRefreshedAtEpochMilliseconds
        val now = clock.nowEpochMilliseconds()

        // A backwards wall-clock jump must not keep a cache permanently fresh.
        if (lastRefresh != null && now >= lastRefresh && now - lastRefresh < FRESHNESS_MILLISECONDS) {
            return FeedUpdateResult.Fresh
        }

        return newsRepository.refresh()
    }

    private companion object {
        const val FRESHNESS_MILLISECONDS = 3_600_000L
    }
}
