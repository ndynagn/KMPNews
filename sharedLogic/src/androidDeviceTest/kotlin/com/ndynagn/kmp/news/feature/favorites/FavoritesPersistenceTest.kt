package com.ndynagn.kmp.news.feature.favorites

import androidx.test.platform.app.InstrumentationRegistry
import com.ndynagn.kmp.news.feature.favorites.data.local.openFavoritesDatabase
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test

class FavoritesPersistenceTest {
    @Test
    fun sqlitePersistsAccountsAndRollsBackFailedPageReplacement() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "favorites-test-${UUID.randomUUID()}.db"
        try {
            verifyFavoritesPersistence { openFavoritesDatabase(context, context.getDatabasePath(name).absolutePath) }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
