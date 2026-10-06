package com.ndynagn.kmp.news.feature.favorites

import androidx.room.useWriterConnection
import com.ndynagn.kmp.news.feature.favorites.data.FavoriteDto
import com.ndynagn.kmp.news.feature.favorites.data.FavoritesCursor
import com.ndynagn.kmp.news.feature.favorites.data.FavoritesPage
import com.ndynagn.kmp.news.feature.favorites.data.local.FavoritesDatabase
import com.ndynagn.kmp.news.feature.favorites.data.local.RoomFavoritesStore
import kotlinx.coroutines.flow.first
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** Shared real-SQLite contract for native/JVM and the separate Android device source set. */
internal suspend fun verifyFavoritesPersistence(open: () -> FavoritesDatabase) {
    val one = FavoriteDto("reader", "one", "2026-10-01T12:00:00.123456Z")
    val two = one.copy(articleId = "two", addedAt = "2026-10-01T12:00:00.123457Z")
    val cursor = FavoritesCursor(one.addedAt, one.articleId)
    val database = open()
    try {
        val store = RoomFavoritesStore(database.favoritesDao())
        store.writePage("reader", FavoritesPage(listOf(one, two), cursor), true)
        store.save(one.copy(userId = "other"))
        assertEquals(listOf("two", "one"), store.observe("reader").first().entries.map { it.articleId })
        assertEquals(1, store.read("other").entries.size)
        store.save(one.copy(title = "authoritative"))
        assertEquals(cursor, store.read("reader").cursor)
        val expected = store.read("reader")

        database.useWriterConnection { connection ->
            connection.usePrepared(
                "CREATE TRIGGER reject_fixture BEFORE INSERT ON favorites " +
                    "WHEN NEW.articleId = 'failure' BEGIN SELECT RAISE(ABORT, 'fixture'); END",
            ) { it.step() }
        }
        assertFails {
            store.writePage("reader", FavoritesPage(listOf(one.copy(articleId = "failure")), null), true)
        }
        assertEquals(expected, store.read("reader"))
    } finally {
        database.close()
    }

    val reopened = open()
    try {
        val store = RoomFavoritesStore(reopened.favoritesDao())
        assertEquals(cursor, store.read("reader").cursor)
        assertEquals("authoritative", store.read("reader").entries.last().title)
        store.remove("reader", "one")
        assertEquals(1, store.read("other").entries.size)
        assertEquals(cursor, store.read("reader").cursor)
        store.writePage("reader", FavoritesPage(emptyList(), null), true)
        assertTrue(store.read("reader").entries.isEmpty())
        assertTrue(store.read("reader").isInitialized)
    } finally {
        reopened.close()
    }
}
