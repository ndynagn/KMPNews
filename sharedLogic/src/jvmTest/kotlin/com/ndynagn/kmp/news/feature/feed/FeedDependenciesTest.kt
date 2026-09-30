package com.ndynagn.kmp.news.feature.feed

import com.ndynagn.kmp.news.feature.feed.data.local.openFeedDatabase
import com.ndynagn.kmp.news.feature.feed.di.assembleFeedDependencies
import com.ndynagn.kmp.news.feature.feed.di.configureFeedHttpClient
import com.ndynagn.kmp.news.feature.feed.di.createFeedDependenciesWithClient
import com.ndynagn.kmp.news.feature.feed.domain.FeedReadResult
import com.ndynagn.kmp.news.feature.feed.domain.FeedUpdateResult
import com.ndynagn.kmp.news.feature.feed.domain.NewsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.koin.core.KoinApplication
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FeedDependenciesTest {
    @Test
    fun closingOneContainerPreservesOtherContainerAndItsCache() = runTest {
        val directory = Files.createTempDirectory("kmpnews-di-").toFile()
        val firstDatabase = openFeedDatabase(directory.resolve("first.db").path)
        val secondDatabase = openFeedDatabase(directory.resolve("second.db").path)
        val firstClient = HttpClient(
            MockEngine {
                respond(
                    """{"status":"success","totalResults":0,"results":[]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) { configureFeedHttpClient() }
        val secondClient = HttpClient(
            MockEngine {
                respond(
                    """{"status":"success","totalResults":0,"results":[]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) { configureFeedHttpClient() }
        val first = assembleFeedDependencies(firstDatabase, firstClient, "fixture-key")
        val second = assembleFeedDependencies(secondDatabase, secondClient, "fixture-key")
        var firstClosed = false

        try {
            assertNotSame(first.newsRepository, second.newsRepository)
            assertNotSame(first.refreshFeedIfNeeded, second.refreshFeedIfNeeded)
            assertEquals(FeedUpdateResult.Updated, first.newsRepository.refresh())

            val untouched = assertIs<FeedReadResult.Snapshot>(second.newsRepository.observeFeed().first())

            assertEquals(null, untouched.feed.lastRefreshedAtEpochMilliseconds)

            first.close()
            firstClosed = true
            requireNotNull(firstClient.coroutineContext[Job]).join()

            assertFails { firstDatabase.feedDao().read() }
            assertFalse(requireNotNull(firstClient.coroutineContext[Job]).isActive)
            assertTrue(requireNotNull(secondClient.coroutineContext[Job]).isActive)
            assertEquals(FeedUpdateResult.Updated, second.newsRepository.refresh())
            assertEquals(FeedUpdateResult.Fresh, second.refreshFeedIfNeeded())
        } finally {
            try {
                try {
                    if (!firstClosed) first.close()
                } finally {
                    second.close()
                }

                requireNotNull(secondClient.coroutineContext[Job]).join()

                assertFails { secondDatabase.feedDao().read() }
                assertFalse(requireNotNull(secondClient.coroutineContext[Job]).isActive)
            } finally {
                directory.deleteRecursively()
            }
        }
    }

    @Test
    fun ownerClosesClientAndDatabaseWhenContainerCloseFails() = runTest {
        val directory = Files.createTempDirectory("kmpnews-close-").toFile()
        val database = openFeedDatabase(directory.resolve("feed.db").path)
        val client = HttpClient(MockEngine { respond("unused") }) { configureFeedHttpClient() }
        val failure = IllegalStateException("container close")
        var closes = 0
        val owner = assembleFeedDependencies(database, client, "fixture-key") { application ->
            application.koin.loadModules(
                listOf(
                    module {
                        single { CloseMarker() } onClose {
                            closes++
                            throw failure
                        }
                    },
                ),
            )
            application.koin.get<CloseMarker>()
        }

        try {
            database.feedDao().read()

            assertSame(failure, assertFailsWith<IllegalStateException> { owner.close() })
            requireNotNull(client.coroutineContext[Job]).join()

            assertEquals(1, closes)
            assertFalse(requireNotNull(client.coroutineContext[Job]).isActive)
            assertFails { database.feedDao().read() }
        } finally {
            try {
                client.close()
            } finally {
                try {
                    database.close()
                } finally {
                    directory.deleteRecursively()
                }
            }
        }
    }

    @Test
    fun failedInitializationClosesAcquiredGraphAndPreservesCancellation() = runTest {
        val directory = Files.createTempDirectory("kmpnews-initialize-").toFile()
        val database = openFeedDatabase(directory.resolve("feed.db").path)
        val client = HttpClient(MockEngine { respond("unused") }) { configureFeedHttpClient() }
        val cancellation = CancellationException("initialization cancelled")
        var application: KoinApplication? = null

        try {
            database.feedDao().read()

            val thrown = assertFailsWith<CancellationException> {
                assembleFeedDependencies(database, client, "fixture-key") {
                    application = it
                    it.koin.get<NewsRepository>()
                    throw cancellation
                }
            }

            assertSame(cancellation, thrown)
            assertFails { requireNotNull(application).koin.get<NewsRepository>() }
            requireNotNull(client.coroutineContext[Job]).join()

            assertFalse(requireNotNull(client.coroutineContext[Job]).isActive)
            assertFails { database.feedDao().read() }
        } finally {
            try {
                client.close()
            } finally {
                try {
                    database.close()
                } finally {
                    directory.deleteRecursively()
                }
            }
        }
    }

    @Test
    fun clientCreationFailureClosesPreviouslyCreatedDatabase() = runTest {
        val directory = Files.createTempDirectory("kmpnews-client-").toFile()
        val database = openFeedDatabase(directory.resolve("feed.db").path)
        val failure = IllegalStateException("client creation")

        try {
            database.feedDao().read()

            val thrown = assertFailsWith<IllegalStateException> {
                createFeedDependenciesWithClient(database, "fixture-key") { throw failure }
            }

            assertSame(failure, thrown)
            assertFails { database.feedDao().read() }
        } finally {
            try {
                database.close()
            } finally {
                directory.deleteRecursively()
            }
        }
    }

    private class CloseMarker
}
