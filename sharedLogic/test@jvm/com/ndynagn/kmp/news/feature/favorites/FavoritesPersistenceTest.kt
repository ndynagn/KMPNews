package com.ndynagn.kmp.news.feature.favorites

import com.ndynagn.kmp.news.feature.favorites.data.local.openFavoritesDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class FavoritesPersistenceTest {
    @Test
    fun sqlitePersistsAccountsAndRollsBackFailedPageReplacement() = runTest {
        val directory = Files.createTempDirectory("kmpnews-favorites-").toFile()
        try {
            verifyFavoritesPersistence { openFavoritesDatabase(directory.resolve("favorites.db").absolutePath) }
        } finally {
            directory.deleteRecursively()
        }
    }
}
