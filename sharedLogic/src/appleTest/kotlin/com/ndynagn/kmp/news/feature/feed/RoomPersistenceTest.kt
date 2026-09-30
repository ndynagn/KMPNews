package com.ndynagn.kmp.news.feature.feed

import androidx.room.useWriterConnection
import com.ndynagn.kmp.news.feature.feed.data.local.CachedFeed
import com.ndynagn.kmp.news.feature.feed.data.local.RoomFeedStore
import com.ndynagn.kmp.news.feature.feed.data.local.openFeedDatabase
import com.ndynagn.kmp.news.feature.feed.domain.Article
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class RoomPersistenceTest {
    @Test
    fun persistsMetadataOrderAndCursorAcrossReopen() = runTest {
        val path = NSTemporaryDirectory() + "kmpnews-" + NSUUID().UUIDString + ".db"
        val expected = CachedFeed(listOf(Article("b"), Article("a")), 123L, "opaque+cursor")
        val database = openFeedDatabase(path)

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

        val reopened = openFeedDatabase(path)

        try {
            assertEquals(expected, RoomFeedStore(reopened.feedDao()).read())
        } finally {
            reopened.close()
            listOf(path, path + "-wal", path + "-shm").forEach {
                NSFileManager.defaultManager.removeItemAtPath(it, null)
            }
        }
    }
}
