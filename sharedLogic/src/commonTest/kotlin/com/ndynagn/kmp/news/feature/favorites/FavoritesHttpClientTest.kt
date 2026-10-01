package com.ndynagn.kmp.news.feature.favorites

import com.ndynagn.kmp.news.feature.favorites.data.FavoriteDto
import com.ndynagn.kmp.news.feature.favorites.data.FavoritesCursor
import com.ndynagn.kmp.news.feature.favorites.data.FavoritesPage
import com.ndynagn.kmp.news.feature.favorites.data.FavoritesResponse
import com.ndynagn.kmp.news.feature.favorites.data.SupabaseFavoritesClient
import com.ndynagn.kmp.news.feature.favorites.data.favoritesJson
import com.ndynagn.kmp.news.feature.favorites.di.configureFavoritesClient
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesFailure
import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.network.AccountCredentials
import com.ndynagn.kmp.news.network.AccountIdentity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.pluginOrNull
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FavoritesHttpClientTest {
    private val credentials = AccountCredentials(AccountIdentity("reader", 1), "private-fixture-access")

    @Test
    fun writesUseSessionAndIdempotenceWithoutTimestampOrLogs() = runTest {
        val id = "odd,+/\\\"id&other=eq.x"
        val client = HttpClient(
            MockEngine { request ->
                assertEquals("/rest/v1/user_favorites", request.url.encodedPath)
                assertEquals("Bearer private-fixture-access", request.headers["Authorization"])
                assertEquals("sb_publishable_fixture", request.headers["apikey"])
                assertEquals("eq.\"reader\"", request.url.parameters["user_id"])
                assertFalse(request.url.toString().contains("private-fixture-access"))
                if (request.method == HttpMethod.Post) {
                    val body = (request.body as TextContent).text
                    assertTrue(body.contains("\"user_id\":\"reader\""))
                    assertFalse(body.contains("added_at"))
                    assertEquals("user_id,article_id", request.url.parameters["on_conflict"])
                    assertEquals("resolution=ignore-duplicates,return=minimal", request.headers["Prefer"])
                } else {
                    assertEquals(HttpMethod.Delete, request.method)
                    assertEquals("eq.\"odd,+/\\\\\\\"id&other=eq.x\"", request.url.parameters["article_id"])
                    assertNull(request.url.parameters["other"])
                }
                respond("", HttpStatusCode.NoContent)
            },
        ) { configureFavoritesClient() }

        try {
            val remote = remote(client)
            assertNull(client.pluginOrNull(Logging))
            assertIs<FavoritesResponse.Success<Unit>>(remote.add(credentials, Article(id)))
            assertIs<FavoritesResponse.Success<Unit>>(remote.remove(credentials, id))
        } finally {
            client.close()
        }
    }

    @Test
    fun pagesPreserveTimestampPrecisionAndEscapeCursorGrammar() = runTest {
        val time = "2026-10-01T12:00:00.123456+00:00"
        val rows = (0..49).map { FavoriteDto("reader", "id-$it", time) }
        var calls = 0
        val client = HttpClient(
            MockEngine { request ->
                calls++
                assertEquals("50", request.url.parameters["limit"])
                assertEquals("added_at.desc,article_id.desc", request.url.parameters["order"])
                if (calls == 1) {
                    assertNull(request.url.parameters["or"])
                    respond(favoritesJson.encodeToString(rows))
                } else {
                    assertEquals(
                        "(added_at.lt.\"$time\",and(added_at.eq.\"$time\",article_id.lt.\"a,\\\"b\\\\c\"))",
                        request.url.parameters["or"],
                    )
                    respond("[]")
                }
            },
        ) { configureFavoritesClient() }

        try {
            val remote = remote(client)
            val first = assertIs<FavoritesResponse.Success<FavoritesPage>>(remote.fetch(credentials, null))
            val page = first.value

            assertEquals(time, page.next?.addedAt)

            val last = remote.fetch(credentials, FavoritesCursor(time, "a,\"b\\c"))

            assertIs<FavoritesResponse.Success<*>>(last)
        } finally {
            client.close()
        }
    }

    @Test
    fun malformedForeignAndProviderErrorsAreSanitized() = runTest {
        val cases = listOf(
            Triple(401, "secret", FavoritesFailure.AUTH_REQUIRED),
            Triple(403, "secret", FavoritesFailure.ACCESS_DENIED),
            Triple(429, "secret", FavoritesFailure.RATE_LIMITED),
            Triple(503, "secret", FavoritesFailure.SERVICE),
            Triple(200, "{}", FavoritesFailure.INVALID_RESPONSE),
            Triple(
                200,
                """[{"user_id":"other","article_id":"one","added_at":"2026-10-01T00:00:00Z"}]""",
                FavoritesFailure.INVALID_RESPONSE,
            ),
            Triple(
                200,
                """[{"user_id":"reader","article_id":"one","added_at":"invalid"}]""",
                FavoritesFailure.INVALID_RESPONSE,
            ),
        )
        for ((status, body, expected) in cases) {
            val client = HttpClient(MockEngine { respond(body, HttpStatusCode.fromValue(status)) }) {
                configureFavoritesClient()
            }
            try {
                assertEquals(FavoritesResponse.Failed(expected), remote(client).fetch(credentials, null))
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun cancellationPropagatesAndInvalidConfigurationMakesNoRequest() = runTest {
        val client = HttpClient(MockEngine { throw CancellationException("cancelled") }) { configureFavoritesClient() }
        try {
            assertEquals(
                FavoritesResponse.Failed(FavoritesFailure.NOT_CONFIGURED),
                SupabaseFavoritesClient(client, "invalid", "", false).fetch(credentials, null),
            )
            var cancelled = false
            try {
                remote(client).fetch(credentials, null)
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        } finally {
            client.close()
        }
    }

    private fun remote(client: HttpClient) =
        SupabaseFavoritesClient(client, "https://example.test", "sb_publishable_fixture", true)
}
