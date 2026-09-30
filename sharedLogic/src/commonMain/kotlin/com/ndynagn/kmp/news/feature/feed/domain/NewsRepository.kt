package com.ndynagn.kmp.news.feature.feed.domain

import kotlinx.coroutines.flow.Flow

/**
 * Shared feed boundary; calls and collection belong to the caller's coroutine lifecycle.
 *
 * One instance serializes mutations for one database. Overlapping refresh/append calls
 * return [FeedUpdateResult.AlreadyRunning] without queueing. Cancellation propagates;
 * a transaction committed before cancellation remains observable.
 * See [feed contract](../../../../../../../../../../../docs/news-feed-data-domain-plan.md) for the canonical feed contract.
 */
interface NewsRepository {
    /**
     * Observes persisted snapshots, including stale data, without requesting the network.
     * Emits [FeedReadResult.StorageFailure] and completes on a storage failure; retry
     * requires a new subscription. Cancelling collection releases that subscription.
     */
    fun observeFeed(): Flow<FeedReadResult>

    /**
     * Fetches page one regardless of freshness, merging cards and replacing the cursor.
     * Successful empty responses retain cards and update freshness. Expected failures
     * return [FeedUpdateResult.Failed] and preserve the previous cache and cursor.
     */
    suspend fun refresh(): FeedUpdateResult

    /**
     * Fetches the stored cursor, or page one when the cache is uninitialized.
     * Initialized data without a cursor returns [FeedUpdateResult.EndReached]; otherwise
     * a full cache returns [FeedUpdateResult.CacheLimitReached]. Neither requests HTTP.
     * Append preserves freshness; expected failures preserve the cache and cursor.
     */
    suspend fun loadNextPage(): FeedUpdateResult
}
