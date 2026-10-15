package com.ndynagn.kmp.news.feature.feed

import android.content.Context
import androidx.room.useWriterConnection
import com.ndynagn.kmp.news.feature.feed.data.local.CachedFeed
import com.ndynagn.kmp.news.feature.feed.data.local.RoomFeedStore
import com.ndynagn.kmp.news.feature.feed.data.local.openFeedDatabase
import com.ndynagn.kmp.news.feature.feed.domain.Article
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlinx.coroutines.flow.first

class FeedDevicePersistence {
    suspend fun verify(context: Context) {
        val path = context.getDatabasePath("feed-test-" + UUID.randomUUID() + ".db").absolutePath
        val expected = CachedFeed(listOf(Article("b"), Article("a")), 123L, "opaque+cursor")
        val database = openFeedDatabase(context, path)

        try {
            val store = RoomFeedStore(database.feedDao())

            assertEquals(CachedFeed(), store.observe().first())

            store.write(expected)

            assertEquals(expected, store.observe().first())

            database.useWriterConnection { connection ->
                connection.usePrepared(
                    "CREATE TRIGGER reject_write BEFORE INSERT ON articles BEGIN SELECT RAISE(ABORT, 'fixture'); END",
                ) { it.step() }
            }

            assertFails { store.write(CachedFeed(listOf(Article("replacement")), 999L, "new-cursor")) }
            assertEquals(expected, store.read())
        } finally {
            database.close()
        }

        val reopened = openFeedDatabase(context, path)

        try {
            assertEquals(expected, RoomFeedStore(reopened.feedDao()).read())
        } finally {
            reopened.close()
            context.deleteDatabase(java.io.File(path).name)
        }
    }
}
