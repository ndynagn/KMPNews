package com.ndynagn.kmp.news.feature.feed.domain

/** Portable expected failures, deliberately excluding raw provider messages and credentials. */
enum class FeedFailure {
    NETWORK,
    TIMEOUT,
    ACCESS_DENIED,
    INVALID_REQUEST,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    SERVER_UNAVAILABLE,
    INVALID_RESPONSE,
    STORAGE,
}
