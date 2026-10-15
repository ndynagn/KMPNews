package com.ndynagn.kmp.news.feature.profile

import com.ndynagn.kmp.news.feature.auth.di.configureAuthClient
import com.ndynagn.kmp.news.feature.profile.data.ProfileResponse
import com.ndynagn.kmp.news.feature.profile.data.SupabaseProfileClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProfileHttpClientTest {
    @Test fun metadataAndStorageUseAuthenticatedEndpointsWithoutExposingTokens() = runTest {
        val paths = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                paths.add(request.url.encodedPath)
                assertEquals("Bearer fixture-token", request.headers["Authorization"])
                assertEquals("sb_publishable_fixture", request.headers["apikey"])

                when (request.url.encodedPath) {
                    "/auth/v1/user" -> {
                        if (request.method == HttpMethod.Put) {
                            assertEquals("""{"data":{"first_name":"Анна"}}""", (request.body as TextContent).text)
                        }

                        respond(
                            """{"id":"reader","email":"reader@example.test","user_metadata":{"first_name":"Анна"}}""",
                        )
                    }

                    "/storage/v1/object/sign/avatars/reader/aaaa.jpg" -> {
                        assertEquals("""{"expiresIn":3600}""", (request.body as TextContent).text)
                        respond("""{"signedURL":"/object/sign/avatars/reader/aaaa.jpg?token=temporary"}""")
                    }

                    "/storage/v1/object/avatars" -> {
                        assertEquals(HttpMethod.Delete, request.method)
                        assertTrue((request.body as TextContent).text.contains("reader/aaaa.jpg"))
                        respond("[]")
                    }

                    else -> respond("{}")
                }
            },
        ) { configureAuthClient() }
        val remote = SupabaseProfileClient(client, "https://example.test", "sb_publishable_fixture", true)

        assertIs<ProfileResponse.Success<*>>(remote.fetch("fixture-token"))
        assertIs<ProfileResponse.Success<*>>(
            remote.update("fixture-token", JsonObject(mapOf("first_name" to JsonPrimitive("Анна")))),
        )
        assertEquals(
            ProfileResponse.Success(
                "https://example.test/storage/v1/object/sign/avatars/reader/aaaa.jpg?token=temporary",
            ),
            remote.sign("fixture-token", "reader/aaaa.jpg"),
        )
        assertIs<ProfileResponse.Success<*>>(remote.delete("fixture-token", listOf("reader/aaaa.jpg")))
        assertEquals(4, paths.size)

        client.close()
    }
}
