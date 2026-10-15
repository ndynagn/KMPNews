package com.ndynagn.kmp.news.feature.feed.di

import com.ndynagn.kmp.news.network.NewsApiConfiguration

/** Public feed configuration. Invalid configuration disables network reads while keeping Room available. */
class FeedApiConfiguration(val supabaseUrl: String, val publishableKey: String) {
    internal val news = NewsApiConfiguration(supabaseUrl, publishableKey)
    val isConfigured: Boolean get() = news.isConfigured
    internal val host: String? get() = news.host
}
