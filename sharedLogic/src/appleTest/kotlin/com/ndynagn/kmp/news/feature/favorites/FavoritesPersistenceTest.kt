package com.ndynagn.kmp.news.feature.favorites

import com.ndynagn.kmp.news.feature.favorites.data.local.openFavoritesDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test

@OptIn(ExperimentalForeignApi::class)
class FavoritesPersistenceTest {
    @Test
    fun sqlitePersistsAccountsAndRollsBackFailedPageReplacement() = runTest {
        val path = NSTemporaryDirectory() + "favorites-" + NSUUID().UUIDString + ".db"
        try {
            verifyFavoritesPersistence { openFavoritesDatabase(path) }
        } finally {
            listOf(path, "$path-wal", "$path-shm").forEach {
                NSFileManager.defaultManager.removeItemAtPath(it, null)
            }
        }
    }
}
