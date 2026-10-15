package com.ndynagn.kmp.news.feature.feed.domain

/** Epoch milliseconds; injectable for deterministic freshness tests. */
fun interface FeedClock {
    fun nowEpochMilliseconds(): Long
}
