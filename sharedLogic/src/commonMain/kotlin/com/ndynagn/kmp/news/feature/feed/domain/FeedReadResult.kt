package com.ndynagn.kmp.news.feature.feed.domain

/** Storage failures are values so no storage exception escapes a Swift Flow consumer. */
sealed interface FeedReadResult {
    data class Snapshot(val feed: FeedSnapshot) : FeedReadResult

    /** Collection completes after this value; retry by subscribing again. */
    data object StorageFailure : FeedReadResult
}
