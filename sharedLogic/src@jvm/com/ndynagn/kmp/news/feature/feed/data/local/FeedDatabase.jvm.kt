package com.ndynagn.kmp.news.feature.feed.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

internal fun openFeedDatabase(path: String): FeedDatabase = Room.databaseBuilder<FeedDatabase>(name = path)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
