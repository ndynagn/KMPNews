package com.ndynagn.kmp.news.feature.search

import com.ndynagn.kmp.news.data.news.configureNewsHttpClient
import com.ndynagn.kmp.news.feature.search.di.assembleSearchDependencies
import com.ndynagn.kmp.news.feature.search.domain.SearchFailure
import com.ndynagn.kmp.news.feature.search.domain.SearchQuery
import com.ndynagn.kmp.news.feature.search.domain.SearchResult
import com.ndynagn.kmp.news.network.NewsApiConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.koin.dsl.module
import org.koin.dsl.onClose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SearchRepositoryTest {
    private val configuration = NewsApiConfiguration("https://fixture.supabase.co", "sb_publishable_fixture")

    @Test
    fun normalizesCodePointsAndRejectsInvalidInputBeforeHttp() = runTest {
        assertEquals("space", SearchQuery.normalize(" \nspace\t"))
        assertEquals("🌍".repeat(100), SearchQuery.normalize("🌍".repeat(100)))
        assertNull(SearchQuery.normalize("🌍".repeat(101)))
        assertEquals("space", SearchQuery.normalize("\uFEFF\u00A0space\u3000"))
        assertEquals("\u0085space\u0085", SearchQuery.normalize("\u0085space\u0085"))
        assertEquals("e\u0301".repeat(50), SearchQuery.normalize("e\u0301".repeat(50)))
        assertNull(SearchQuery.normalize("e\u0301".repeat(51)))
        val client = HttpClient(MockEngine { error("Invalid input must not reach HTTP") })
        val dependencies = assembleSearchDependencies(client, configuration)
        try {
            for (input in listOf("", " \n ", "x".repeat(101))) {
                assertEquals(
                    SearchResult.Failed(SearchFailure.INVALID_QUERY),
                    dependencies.searchRepository.search(input, null),
                )
            }
        } finally {
            dependencies.close()
        }
    }

    @Test
    fun graphNeedsNoStorageAndSendsNormalizedQueryAndOpaquePage() = runTest {
        val client = HttpClient(
            MockEngine { request ->
                assertEquals("space & science", request.url.parameters["q"])
                assertEquals("next+/=", request.url.parameters["page"])
                assertEquals(configuration.publishableKey, request.headers["apikey"])
                assertNull(request.url.parameters["apikey"])
                respond(
                    """{"status":"success","totalResults":1,"results":[{"article_id":"a"}],"nextPage":"b"}""",
                    headers = headersOf("Content-Type", "application/json"),
                )
            },
        ) { configureNewsHttpClient(configuration) }
        val dependencies = assembleSearchDependencies(client, configuration)
        try {
            val page = assertIs<SearchResult.Page>(dependencies.searchRepository.search(" space & science ", "next+/="))
            assertEquals("a", page.articles.single().id)
            assertEquals("b", page.nextCursor)
        } finally {
            dependencies.close()
        }
    }

    @Test
    fun keepsQuotaAndRateFailuresDistinctAndNeverReturnsAnEmptySuccess() = runTest {
        for ((code, expected) in listOf(
            "ApiLimitExceeded" to SearchFailure.QUOTA_EXCEEDED,
            "RateLimitExceeded" to SearchFailure.RATE_LIMITED,
            "InvalidResponse" to SearchFailure.INVALID_RESPONSE,
            "InvalidRequest" to SearchFailure.INVALID_QUERY,
            "UpstreamTimeout" to SearchFailure.TIMEOUT,
            "Unauthorized" to SearchFailure.ACCESS_DENIED,
            "ServiceUnavailable" to SearchFailure.SERVICE,
        )) {
            val client = HttpClient(
                MockEngine {
                    respond(
                        """{"status":"error","results":{"code":"$code"}}""",
                        HttpStatusCode.TooManyRequests,
                        headersOf("Content-Type", "application/json"),
                    )
                },
            ) { configureNewsHttpClient(configuration) }
            val dependencies = assembleSearchDependencies(client, configuration)
            try {
                assertEquals(SearchResult.Failed(expected), dependencies.searchRepository.search("space", null))
            } finally {
                dependencies.close()
            }
        }
    }

    @Test
    fun emptySuccessMalformedResponseAndHttpErrorsRemainDistinct() = runTest {
        val cases = listOf(
            Triple(200, """{"status":"success","totalResults":0,"results":[]}""", SearchResult.Page(emptyList(), null)),
            Triple(200, "{", SearchResult.Failed(SearchFailure.INVALID_RESPONSE)),
            Triple(200, """{"status":"success","results":{}}""", SearchResult.Failed(SearchFailure.INVALID_RESPONSE)),
            Triple(503, "unavailable", SearchResult.Failed(SearchFailure.SERVICE)),
            Triple(401, "denied", SearchResult.Failed(SearchFailure.ACCESS_DENIED)),
            Triple(429, "limited", SearchResult.Failed(SearchFailure.RATE_LIMITED)),
        )
        for ((status, body, expected) in cases) {
            val client = HttpClient(
                MockEngine {
                    respond(body, HttpStatusCode.fromValue(status), headersOf("Content-Type", "application/json"))
                },
            ) { configureNewsHttpClient(configuration) }
            val dependencies = assembleSearchDependencies(client, configuration)
            try {
                assertEquals(expected, dependencies.searchRepository.search("space", null))
            } finally {
                dependencies.close()
            }
        }
    }

    @Test
    fun configurationNetworkAndTimeoutHaveDistinctFailures() = runTest {
        for (expected in listOf(SearchFailure.NOT_CONFIGURED, SearchFailure.NETWORK, SearchFailure.TIMEOUT)) {
            var requests = 0
            val config = if (expected == SearchFailure.NOT_CONFIGURED) NewsApiConfiguration("", "") else configuration
            val client = HttpClient(
                MockEngine { request ->
                    requests++
                    if (expected == SearchFailure.TIMEOUT) throw HttpRequestTimeoutException(request)
                    throw IllegalStateException("synthetic network failure")
                },
            ) { configureNewsHttpClient(config) }
            val dependencies = assembleSearchDependencies(client, config)
            try {
                assertEquals(SearchResult.Failed(expected), dependencies.searchRepository.search("space", null))
                assertEquals(if (expected == SearchFailure.NOT_CONFIGURED) 0 else 1, requests)
            } finally {
                dependencies.close()
            }
        }
    }

    @Test
    fun closesHttpWhenGraphCleanupFailsAndPreservesAssemblyCancellation() = runTest {
        val client = HttpClient(MockEngine { error("No request expected") })
        val failure = IllegalStateException("graph cleanup")
        val dependencies = assembleSearchDependencies(client, configuration) { application ->
            application.koin.loadModules(
                listOf(
                    module {
                        single { CloseMarker() } onClose { throw failure }
                    },
                ),
            )
            application.koin.get<CloseMarker>()
        }
        assertSame(failure, assertFailsWith<IllegalStateException> { dependencies.close() })
        requireNotNull(client.coroutineContext[Job]).join()
        assertFalse(requireNotNull(client.coroutineContext[Job]).isActive)

        val secondClient = HttpClient(MockEngine { error("No request expected") })
        val cancellation = CancellationException("assembly cancelled")
        assertSame(
            cancellation,
            assertFailsWith<CancellationException> {
                assembleSearchDependencies(secondClient, configuration) { throw cancellation }
            },
        )
        requireNotNull(secondClient.coroutineContext[Job]).join()
        assertFalse(requireNotNull(secondClient.coroutineContext[Job]).isActive)
    }

    @Test
    fun cancellationReachesHttp() = runTest {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val client = HttpClient(
            MockEngine {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            },
        ) { configureNewsHttpClient(configuration) }
        val dependencies = assembleSearchDependencies(client, configuration)
        try {
            val job = async { dependencies.searchRepository.search("space", null) }
            started.await()
            job.cancelAndJoin()
            cancelled.await()
            assertTrue(job.isCancelled)
        } finally {
            dependencies.close()
        }
    }
}

private class CloseMarker
