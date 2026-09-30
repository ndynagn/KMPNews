package com.ndynagn.kmp.news.feature.feed

import androidx.room.useWriterConnection
import androidx.test.platform.app.InstrumentationRegistry
import com.ndynagn.kmp.news.feature.feed.data.local.CachedFeed
import com.ndynagn.kmp.news.feature.feed.data.local.RoomFeedStore
import com.ndynagn.kmp.news.feature.feed.data.local.openFeedDatabase
import com.ndynagn.kmp.news.feature.feed.domain.Article
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class RoomPersistenceTest {
    @Test
    fun persistsMetadataOrderAndCursorAcrossReopen() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
