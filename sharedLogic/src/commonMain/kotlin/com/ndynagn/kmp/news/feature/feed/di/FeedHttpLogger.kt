package com.ndynagn.kmp.news.feature.feed.di

/**
 * Receives header-level HTTP diagnostics when explicitly supplied by a composition root.
 *
 * Calls may arrive concurrently on different threads. Implementations must return quickly,
 * be thread-safe and never throw. Bodies are omitted and credential headers are masked;
 * URLs and ordinary headers remain diagnostic data. Enable only for local debugging.
 * The client retains this sink until closed; it does not own or close the sink.
 */
fun interface FeedHttpLogger {
    /** Writes one diagnostic message without introducing application or UI work. */
    fun log(message: String)
}
