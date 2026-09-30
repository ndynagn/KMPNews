package com.ndynagn.kmp.news

import android.app.Application
import com.ndynagn.kmp.news.feature.feed.di.FeedApiConfiguration
import com.ndynagn.kmp.news.feature.feed.di.createFeedDependencies

/** One graph per process; Android releases these resources when the process ends. */
class NewsApplication : Application() {
    val feedConfiguration = FeedApiConfiguration(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)

    val feedDependencies by lazy {
        createFeedDependencies(this, getDatabasePath("news-feed.db").absolutePath, feedConfiguration)
    }
}
