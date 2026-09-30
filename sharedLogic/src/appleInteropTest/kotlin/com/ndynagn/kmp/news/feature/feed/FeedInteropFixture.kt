package com.ndynagn.kmp.news.feature.feed

import com.ndynagn.kmp.news.feature.feed.data.OfflineFirstNewsRepository
import com.ndynagn.kmp.news.feature.feed.data.local.RoomFeedStore
import com.ndynagn.kmp.news.feature.feed.data.local.openFeedDatabase
import com.ndynagn.kmp.news.feature.feed.data.remote.NewsFeedClient
import com.ndynagn.kmp.news.feature.feed.data.remote.PageResult
import com.ndynagn.kmp.news.feature.feed.di.FeedApiConfiguration
import com.ndynagn.kmp.news.feature.feed.di.FeedHttpLogger
import com.ndynagn.kmp.news.feature.feed.di.configureFeedHttpClient
import com.ndynagn.kmp.news.feature.feed.domain.FeedClock
import com.ndynagn.kmp.news.feature.feed.domain.FeedReadResult
import com.ndynagn.kmp.news.feature.feed.domain.FeedUpdateResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/** Only compiled with -PfeedInteropTests=true; never present in normal app frameworks. */
class FeedInteropFixture(databasePath: String, private val shouldFail: Boolean) {
    private val started = CompletableDeferred<Unit>()
    private val responseAllowed = CompletableDeferred<Unit>()
    private val finished = CompletableDeferred<Unit>()
    private val observers = MutableStateFlow(0)
    private val requests = MutableStateFlow(0)
    private val database = openFeedDatabase(databasePath)
    private val httpClient = HttpClient(
        MockEngine {
            requests.update { it + 1 }
            started.complete(Unit)
            try {
                responseAllowed.await()

                if (shouldFail) {
                    respond(
                        """{"status":"error","results":{"code":"ApiLimitExceeded"}}""",
                        HttpStatusCode.TooManyRequests,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    respond(
                        """
{
  "status": "success",
  "totalResults": 1,
  "results": [
    {
      "article_id": "fixture",
      "title": null,
      "description": "summary",
      "pubDate": "1970-01-01 00:00:01"
    }
  ]
}
                        """.trimIndent(),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            } finally {
                requests.update { it - 1 }
                finished.complete(Unit)
            }
        },
    ) { configureFeedHttpClient(feedConfiguration) }
    private val repository = OfflineFirstNewsRepository(
        RoomFeedStore(database.feedDao()),
        NewsFeedClient(httpClient, feedConfiguration),
        FeedClock { 123_000L },
    )

    val activeObservers: Int get() = observers.value
    val activeRequests: Int get() = requests.value

    fun observeFeed(): Flow<FeedReadResult> = repository.observeFeed()
        .onStart { observers.update { it + 1 } }
        .onCompletion { observers.update { it - 1 } }

    suspend fun refresh(): FeedUpdateResult = repository.refresh()

    /** Exercises a Swift-owned logger through the real Ktor plugin with synthetic transport. */
    suspend fun verifyHttpLogging(httpLogger: FeedHttpLogger) {
        val client = HttpClient(
            MockEngine {
                respond(
                    """{"status":"success","totalResults":0,"results":[]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) { configureFeedHttpClient(feedConfiguration, httpLogger) }

        try {
            check(NewsFeedClient(client, feedConfiguration).fetch(null) is PageResult.Success)
        } finally {
            client.close()
        }
    }

    suspend fun awaitRequestStarted() {
        started.await()
    }

    suspend fun awaitRequestFinished() {
        finished.await()
    }

    fun allowResponse() {
        responseAllowed.complete(Unit)
    }

    fun close() {
        httpClient.close()
        database.close()
    }
}

private val feedConfiguration = FeedApiConfiguration("https://fixture.supabase.co", "sb_publishable_fixture-key")
