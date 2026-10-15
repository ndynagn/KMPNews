package com.ndynagn.kmp.news.feature.favorites.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal fun openFavoritesDatabase(path: String): FavoritesDatabase =
    Room.databaseBuilder<FavoritesDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
