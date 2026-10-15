package com.ndynagn.kmp.news.feature.feed

import com.ndynagn.kmp.news.feature.feed.data.remote.NewsFeedClient
import com.ndynagn.kmp.news.feature.feed.data.remote.PageResult
import com.ndynagn.kmp.news.feature.feed.di.FeedApiConfiguration
import com.ndynagn.kmp.news.feature.feed.di.configureFeedHttpClient
import com.ndynagn.kmp.news.feature.feed.domain.FeedFailure
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewsFeedClientTest {
    @Test
    fun sendsFixedQueryHeaderAndOpaqueCursorWithoutKeyInUrl() = runTest {
        val engine = MockEngine { request ->
            assertEquals("https", request.url.protocol.name)
            assertEquals("fixture.supabase.co", request.url.host)
            assertEquals("/functions/v1/news-feed", request.url.encodedPath)
            assertEquals("sb_publishable_fixture-key", request.headers["apikey"])
            assertNull(request.headers["X-ACCESS-KEY"])
            assertNull(request.headers["Authorization"])
            assertEquals("application/json", request.headers[HttpHeaders.Accept])
            assertNull(request.url.parameters["language"])
            assertNull(request.url.parameters["size"])
            assertNull(request.url.parameters["timezone"])
            assertEquals("next+/=", request.url.parameters["page"])
            assertNull(request.url.parameters["country"])
            assertTrue(!request.url.toString().contains("sb_publishable_fixture-key"))

            respond(
                """
{
  "status": "success",
  "totalResults": 1,
  "results": [
    {
      "article_id": "a",
      "title": null,
      "unknown": true
    }
  ],
  "nextPage": "next"
}
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) { configureFeedHttpClient(feedConfiguration) }

        try {
            val page = assertIs<PageResult.Success>(NewsFeedClient(client, feedConfiguration).fetch("next+/=")).page

            assertNull(page.articles.single().title)
            assertNull(page.articles.single().publishedAtEpochMilliseconds)
            assertEquals("next", page.nextPage)
        } finally {
            client.close()
        }
    }

    @Test
    fun parsesUtcAndRejectsMalformedDatesAndMissingIds() = runTest {
        val good =
            fetch(
                """
{
  "status": "success",
  "totalResults": 1,
  "results": [
    {
      "article_id": "a",
      "pubDate": "1970-01-01 00:00:01",
      "pubDateTZ": "UTC"
    }
  ]
}
                """.trimIndent(),
            )
        assertEquals(1000L, assertIs<PageResult.Success>(good).page.articles.single().publishedAtEpochMilliseconds)
        for (article in listOf("{}", """{"article_id":"a","pubDate":"bad"}""")) {
            assertEquals(
                PageResult.Failure(FeedFailure.INVALID_RESPONSE),
                fetch("""{"status":"success","totalResults":1,"results":[$article]}"""),
            )
        }

        assertEquals(PageResult.Failure(FeedFailure.INVALID_RESPONSE), fetch("not json"))
    }

    @Test
    fun mapsObjectAndListErrorsWithoutExposingMessages() = runTest {
        for (details in listOf(
            """{"code":"ApiLimitExceeded","message":"private"}""",
            """[{"code":"ApiLimitExceeded","message":"private"}]""",
        )) {
            assertEquals(
                PageResult.Failure(FeedFailure.QUOTA_EXCEEDED),
                fetch("""{"status":"error","results":$details}""", 429),
            )
        }

        assertEquals(
            PageResult.Failure(FeedFailure.SERVER_UNAVAILABLE),
            fetch("""{"status":"error","results":{"code":"ServiceUnavailable "}}""", 503),
        )
        assertEquals(PageResult.Failure(FeedFailure.ACCESS_DENIED), fetch("bad body", 401))
        assertEquals(PageResult.Failure(FeedFailure.INVALID_REQUEST), fetch("{}", 422))
        assertEquals(PageResult.Failure(FeedFailure.RATE_LIMITED), fetch("{}", 429))
    }

    @Test
    fun acceptsEmptySuccessAndRejectsInvalidJsonShapes() = runTest {
        val result = fetch("""{"status":"success","totalResults":0,"results":[],"unknown":true}""")
        val page = assertIs<PageResult.Success>(result).page

        assertTrue(page.articles.isEmpty())
        assertNull(page.nextPage)

        for (body in listOf("[]", "null", "{", """{"status":"success","results":{}}""")) {
            assertEquals(PageResult.Failure(FeedFailure.INVALID_RESPONSE), fetch(body))
        }
    }

    @Test
    fun preservesHttpFailureWhenContentTypeCannotBeConverted() = runTest {
        assertEquals(
            PageResult.Failure(FeedFailure.SERVER_UNAVAILABLE),
            fetch("<html>Unavailable</html>", 503, "text/html"),
        )
        assertEquals(
            PageResult.Failure(FeedFailure.INVALID_RESPONSE),
            fetch("plain text", 200, "text/plain"),
        )
        assertEquals(
            PageResult.Failure(FeedFailure.QUOTA_EXCEEDED),
            fetch("""{"status":"error","results":{"code":"ApiLimitExceeded"}}"""),
        )
    }

    @Test
    fun transportFailureAndTimeoutRemainDistinct() = runTest {
        for (timeout in listOf(false, true)) {
            val client = HttpClient(
                MockEngine { request ->
                    if (timeout) throw HttpRequestTimeoutException(request)

                    throw IOException("synthetic transport failure")
                },
            ) { configureFeedHttpClient(feedConfiguration) }

            try {
                val expected = if (timeout) FeedFailure.TIMEOUT else FeedFailure.NETWORK

                assertEquals(PageResult.Failure(expected), NewsFeedClient(client, feedConfiguration).fetch(null))
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun missingKeyDoesNotSendARequest() = runTest {
        val client =
            HttpClient(MockEngine { error("Unexpected request") }) { configureFeedHttpClient(feedConfiguration) }

        try {
            assertEquals(
                PageResult.Failure(FeedFailure.ACCESS_DENIED),
                NewsFeedClient(client, FeedApiConfiguration("", "")).fetch(null),
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun mapsMediatorTimeoutAndInvalidResponseSeparatelyFromServerFailure() = runTest {
        assertEquals(
            PageResult.Failure(FeedFailure.TIMEOUT),
            fetch("""{"status":"error","results":{"code":"UpstreamTimeout"}}""", 504),
        )
        assertEquals(
            PageResult.Failure(FeedFailure.INVALID_RESPONSE),
            fetch("""{"status":"error","results":{"code":"InvalidResponse"}}""", 502),
        )
    }

    @Test
    fun invalidOriginsNeverSendCredentials() = runTest {
        val client =
            HttpClient(MockEngine { error("Unexpected request") }) { configureFeedHttpClient(feedConfiguration) }
        try {
            for (origin in listOf(
                "",
                "http://fixture.supabase.co",
                "https://user:password@fixture.supabase.co",
                "https://fixture.supabase.co/path",
                "https://fixture.supabase.co?key=x",
            )) {
                val configuration = FeedApiConfiguration(origin, "sb_publishable_fixture-key")
                assertEquals(
                    PageResult.Failure(FeedFailure.ACCESS_DENIED),
                    NewsFeedClient(client, configuration).fetch(null),
                )
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun cancellationReachesHttpEngine() = runTest {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val client = HttpClient(
            MockEngine {
                entered.complete(Unit)
                try {
                    CompletableDeferred<Unit>().await()
                    respond("unreachable")
                } finally {
                    cancelled.complete(Unit)
                }
            },
        ) { configureFeedHttpClient(feedConfiguration) }

        try {
            val task = async { NewsFeedClient(client, feedConfiguration).fetch(null) }
            entered.await()
            task.cancelAndJoin()
            cancelled.await()

            assertTrue(task.isCancelled)
        } finally {
            client.close()
        }
    }

    private suspend fun fetch(body: String, status: Int = 200, contentType: String = "application/json"): PageResult {
        val client = HttpClient(
            MockEngine {
                respond(body, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, contentType))
            },
        ) { configureFeedHttpClient(feedConfiguration) }

        return try {
            NewsFeedClient(client, feedConfiguration).fetch(null)
        } finally {
            client.close()
        }
    }
}

private val feedConfiguration = FeedApiConfiguration("https://fixture.supabase.co", "sb_publishable_fixture-key")
