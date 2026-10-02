package com.ndynagn.kmp.news.feature.favorites.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

internal fun openFavoritesDatabase(context: android.content.Context, path: String): FavoritesDatabase =
    Room.databaseBuilder<FavoritesDatabase>(context = context.applicationContext, name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
