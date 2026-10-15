package com.ndynagn.kmp.news.feature.favorites

import android.content.Context
import com.ndynagn.kmp.news.feature.favorites.data.local.openFavoritesDatabase
import java.util.UUID

/** Runs the archived real-SQLite contract only in the disposable Android verification checkout. */
class FavoritesDevicePersistence {
    suspend fun verify(context: Context) {
        val name = "favorites-test-${UUID.randomUUID()}.db"

        try {
            verifyFavoritesPersistence { openFavoritesDatabase(context, context.getDatabasePath(name).absolutePath) }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
