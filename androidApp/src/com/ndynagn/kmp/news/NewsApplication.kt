package com.ndynagn.kmp.news

import android.app.Application
import com.ndynagn.kmp.news.feature.auth.createAuthDependencies
import com.ndynagn.kmp.news.feature.auth.di.AuthConfiguration
import com.ndynagn.kmp.news.feature.feed.di.FeedApiConfiguration
import com.ndynagn.kmp.news.feature.feed.di.createFeedDependencies

/** Owns the account graph for the process; screen owners never close these resources. */
class NewsApplication : Application() {
    val feedConfiguration by lazy {
        FeedApiConfiguration(getString(R.string.supabase_url), getString(R.string.supabase_publishable_key))
    }

    val authDependencies by lazy {
        createAuthDependencies(
            this,
            AuthConfiguration(getString(R.string.supabase_url), getString(R.string.supabase_publishable_key)),
        )
    }

    val feedDependencies by lazy {
        createFeedDependencies(this, getDatabasePath("news-feed.db").absolutePath, feedConfiguration)
    }
}
