package com.ndynagn.kmp.news.feature.feed.domain

/** Updated is emitted only after the database transaction commits. Cancellation is thrown. */
sealed interface FeedUpdateResult {
    /** A database update committed; its data remains valid even if return delivery is cancelled. */
    data object Updated : FeedUpdateResult

    /** Automatic refresh was skipped because the initialized cache is less than one hour old. */
    data object Fresh : FeedUpdateResult

    /** Initialized data has no server cursor; no request was made. */
    data object EndReached : FeedUpdateResult

    /** A server cursor exists, but local capacity prevents append; refresh is still allowed. */
    data object CacheLimitReached : FeedUpdateResult

    /** Another mutation owns this repository; this call did not queue or request data. */
    data object AlreadyRunning : FeedUpdateResult

    /** Expected failure; the previous committed snapshot and cursor are preserved. */
    data class Failed(val failure: FeedFailure) : FeedUpdateResult
}
