package com.ndynagn.kmp.news.feature.feed

import com.ndynagn.kmp.news.feature.feed.di.FeedApiConfiguration
import com.ndynagn.kmp.news.feature.feed.di.FeedHttpLogger
import com.ndynagn.kmp.news.feature.feed.di.configureFeedHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedHttpLoggingTest {
    @Test
    fun masksCredentialsAndOmitsBodiesForSuccessAndFailure() = runTest {
        for (status in listOf(HttpStatusCode.OK, HttpStatusCode.Unauthorized)) {
            val messages = Channel<String>(Channel.UNLIMITED)
            val client = HttpClient(
                MockEngine {
                    respond(
                        "private-response-body",
                        status,
                        headersOf("Set-Cookie", "private-response-cookie"),
                    )
                },
            ) { configureFeedHttpClient(feedConfiguration, FeedHttpLogger { messages.trySend(it) }) }

            try {
                val response = client.get("news-feed") {
                    header("apikey", "private-api-key")
                    header("Authorization", "private-authorization")
                    header("Proxy-Authorization", "private-proxy")
                    header("Cookie", "private-cookie")
                    header("X-Diagnostic", "visible-header")
                }

                assertEquals(status, response.status)
                assertEquals("private-response-body", response.bodyAsText())
            } finally {
                client.close()
            }

            val output = drain(messages)

            assertTrue(output.contains("fixture.supabase.co/functions/v1/news-feed"))
            assertTrue(output.contains(status.value.toString()))
            assertTrue(output.contains("visible-header"))
            assertFalse(output.contains("private-"))
        }
    }

    @Test
    fun excludesOtherHostsAndPlainHttpAndIsDisabledByDefault() = runTest {
        val messages = Channel<String>(Channel.UNLIMITED)
        val client = HttpClient(MockEngine { respond("unused") }) {
            configureFeedHttpClient(feedConfiguration, FeedHttpLogger { messages.trySend(it) })
        }
        val quietClient = HttpClient(MockEngine { respond("unused") }) { configureFeedHttpClient(feedConfiguration) }

        try {
            for (url in listOf(
                "https://example.com/",
                "https://fixture.supabase.co.example.com/",
                "http://fixture.supabase.co/",
            )) {
                client.get(url).bodyAsText()
            }

            quietClient.get("news-feed").bodyAsText()

            assertNull(quietClient.pluginOrNull(Logging))
            assertEquals("", drain(messages))
        } finally {
            client.close()
            quietClient.close()
        }
    }

    @Test
    fun loggingPreservesCancellationWithoutExposingCredential() = runTest {
        val entered = CompletableDeferred<Unit>()
        val messages = Channel<String>(Channel.UNLIMITED)
        val client = HttpClient(
            MockEngine {
                entered.complete(Unit)
                CompletableDeferred<Unit>().await()
                respond("unreachable")
            },
        ) { configureFeedHttpClient(feedConfiguration, FeedHttpLogger { messages.trySend(it) }) }

        try {
            val task = async {
                client.get("news-feed") { header("apikey", "private-api-key") }
            }

            entered.await()
            task.cancelAndJoin()

            assertTrue(task.isCancelled)
            assertFalse(drain(messages).contains("private-api-key"))
        } finally {
            client.close()
        }
    }

    private fun drain(messages: Channel<String>): String = buildString {
        while (true) {
            val message = messages.tryReceive().getOrNull() ?: break

            appendLine(message)
        }
    }
}

private val feedConfiguration = FeedApiConfiguration("https://fixture.supabase.co", "sb_publishable_fixture-key")
