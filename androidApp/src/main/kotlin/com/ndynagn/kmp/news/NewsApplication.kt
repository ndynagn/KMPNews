package com.ndynagn.kmp.news

import android.app.Application
import com.ndynagn.kmp.news.feature.auth.createAuthDependencies
import com.ndynagn.kmp.news.feature.auth.di.AuthConfiguration
import com.ndynagn.kmp.news.feature.feed.di.FeedApiConfiguration
import com.ndynagn.kmp.news.feature.feed.di.createFeedDependencies

/** One graph per process; Android releases these resources when the process ends. */
class NewsApplication : Application() {
    val feedConfiguration = FeedApiConfiguration(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)

    val authDependencies by lazy {
        createAuthDependencies(this, AuthConfiguration(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY))
    }

    val feedDependencies by lazy {
        createFeedDependencies(this, getDatabasePath("news-feed.db").absolutePath, feedConfiguration)
    }
}
