package com.ndynagn.kmp.news.feature.auth

import com.ndynagn.kmp.news.feature.auth.data.AuthResponse
import com.ndynagn.kmp.news.feature.auth.data.SupabaseAuthClient
import com.ndynagn.kmp.news.feature.auth.di.AuthConfiguration
import com.ndynagn.kmp.news.feature.auth.di.configureAuthClient
import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import com.ndynagn.kmp.news.feature.profile.domain.ProfileDetails
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.pluginOrNull
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthHttpClientTest {
    @Test
    fun authErrorCodeTakesPrecedenceOverNumericHttpCode() = runTest {
        val client = HttpClient(
            MockEngine {
                respond(
                    """{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}""",
                    HttpStatusCode.BadRequest,
                )
            },
        ) { configureAuthClient() }
        val remote = SupabaseAuthClient(client, AuthConfiguration("https://example.test", "sb_publishable_fixture"))

        assertEquals(
            AuthResponse.Failed(AuthFailure.INVALID_CREDENTIALS),
            remote.signIn("reader@example.test", "incorrect-password"),
        )
        client.close()
    }

    @Test
    fun deletedAccountIsAnExpiredSessionInsteadOfRetryableServiceFailure() = runTest {
        val client = HttpClient(
            MockEngine {
                respond("""{"code":"user_not_found"}""", HttpStatusCode.Forbidden)
            },
        ) { configureAuthClient() }
        val remote = SupabaseAuthClient(client, AuthConfiguration("https://example.test", "sb_publishable_fixture"))

        assertEquals(AuthResponse.Failed(AuthFailure.SESSION_EXPIRED), remote.user("deleted-user-token"))

        client.close()
    }

    @Test
    fun registrationSendsNamesAsUserMetadataAndKeepsPasswordUnmodified() = runTest {
        val client = HttpClient(
            MockEngine { request ->
                val body = Json.parseToJsonElement((request.body as TextContent).text) as JsonObject
                val data = body["data"] as JsonObject

                assertEquals(JsonPrimitive(" password "), body["password"])
                assertEquals(JsonPrimitive("Анна-Мария"), data["first_name"])
                assertEquals(JsonNull, data["middle_name"])
                assertEquals("/auth/v1/signup", request.url.encodedPath)

                respond("{}")
            },
        ) { configureAuthClient() }
        val remote = SupabaseAuthClient(client, AuthConfiguration("https://example.test", "sb_publishable_fixture"))

        assertIs<AuthResponse.Success<Unit>>(
            remote.registerWithProfile(
                "reader@example.test",
                " password ",
                ProfileDetails("Анна-Мария", "O’Connor", null),
            ),
        )

        client.close()
    }

    @Test
    fun signupAndConfirmationUsePasswordAndSignupCodeWithoutCredentialLogging() = runTest {
        val requests = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                val text = (request.body as TextContent).text
                requests += request.url.encodedPath
                assertEquals("sb_publishable_fixture", request.headers["apikey"])
                assertNull(request.headers["Authorization"])
                if (request.url.encodedPath.endsWith("signup")) {
                    assertTrue(text.contains(" spaced password "))
                    respond("{}")
                } else {
                    assertTrue(text.contains("012345"))
                    assertTrue(text.contains("signup"))
                    respond(
                        """{"access_token":"access","refresh_token":"refresh","expires_in":3600,"user":{"id":"reader","email":"reader@example.test"}}""",
                    )
                }
            },
        ) { configureAuthClient() }
        val remote = SupabaseAuthClient(client, AuthConfiguration("https://example.test", "sb_publishable_fixture"))

        assertNull(client.pluginOrNull(Logging))
        assertIs<AuthResponse.Success<Unit>>(remote.register("reader@example.test", " spaced password "))
        assertIs<AuthResponse.Success<*>>(remote.confirm("reader@example.test", "012345"))
        assertEquals(listOf("/auth/v1/signup", "/auth/v1/verify"), requests)
        client.close()
    }

    @Test
    fun serverErrorsMapToSafeTypedFailures() = runTest {
        val cases = listOf(
            Triple("invalid_credentials", HttpStatusCode.BadRequest, AuthFailure.INVALID_CREDENTIALS),
            Triple("email_not_confirmed", HttpStatusCode.BadRequest, AuthFailure.EMAIL_UNCONFIRMED),
            Triple("weak_password", HttpStatusCode.BadRequest, AuthFailure.WEAK_PASSWORD),
            Triple("otp_expired", HttpStatusCode.Forbidden, AuthFailure.INVALID_CODE),
            Triple("over_request_rate_limit", HttpStatusCode.TooManyRequests, AuthFailure.RATE_LIMITED),
            Triple("unexpected_failure", HttpStatusCode.InternalServerError, AuthFailure.SERVICE),
        )
        for ((code, status, failure) in cases) {
            val client =
                HttpClient(
                    MockEngine {
                        respond("""{"code":"$code","msg":"private provider details"}""", status)
                    },
                ) { configureAuthClient() }
            val remote = SupabaseAuthClient(client, AuthConfiguration("https://example.test", "sb_publishable_fixture"))
            assertEquals(AuthResponse.Failed(failure), remote.signIn("reader@example.test", "password"))
            client.close()
        }
    }

    @Test
    fun refreshAndLogoutUseExactTokenContractAndLocalScope() = runTest {
        val paths = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                paths += request.url.encodedPath
                when {
                    request.url.encodedPath.endsWith("token") -> {
                        assertEquals("refresh_token", request.url.parameters["grant_type"])
                        assertNull(request.headers["Authorization"])
                        assertTrue((request.body as TextContent).text.contains("old-refresh"))
                        respond(
                            """{"access_token":"access","refresh_token":"refresh","expires_in":3600,"user":{"id":"reader","email":"reader@example.test"}}""",
                        )
                    }

                    request.url.encodedPath.endsWith("user") -> {
                        assertEquals("Bearer access", request.headers["Authorization"])
                        respond("""{"id":"reader","email":"reader@example.test"}""")
                    }

                    else -> {
                        assertEquals("local", request.url.parameters["scope"])
                        assertEquals("Bearer access", request.headers["Authorization"])
                        respond("", HttpStatusCode.NoContent)
                    }
                }
            },
        ) { configureAuthClient() }
        val remote = SupabaseAuthClient(client, AuthConfiguration("https://example.test", "sb_publishable_fixture"))
        assertIs<AuthResponse.Success<*>>(remote.refresh("old-refresh"))
        assertIs<AuthResponse.Success<*>>(remote.user("access"))
        assertIs<AuthResponse.Success<*>>(remote.logout("access"))
        assertEquals(listOf("/auth/v1/token", "/auth/v1/user", "/auth/v1/logout"), paths)
        client.close()
    }

    @Test
    fun unknownRefreshErrorDoesNotDeclareSessionRevoked() = runTest {
        val client =
            HttpClient(
                MockEngine {
                    respond("""{"code":"validation_failed"}""", HttpStatusCode.BadRequest)
                },
            ) { configureAuthClient() }
        val remote = SupabaseAuthClient(client, AuthConfiguration("https://example.test", "sb_publishable_fixture"))
        assertEquals(AuthResponse.Failed(AuthFailure.SERVICE), remote.refresh("saved-refresh"))
        client.close()
    }

    @Test
    fun existingAccountSignupUsesTheSameConfirmationAcknowledgement() = runTest {
        for (code in listOf("user_already_exists", "email_exists")) {
            val client =
                HttpClient(
                    MockEngine {
                        respond("""{"code":"$code"}""", HttpStatusCode.BadRequest)
                    },
                ) { configureAuthClient() }
            val remote = SupabaseAuthClient(client, AuthConfiguration("https://example.test", "sb_publishable_fixture"))
            assertIs<AuthResponse.Success<Unit>>(remote.register("reader@example.test", "password"))
            client.close()
        }
    }

    @Test
    fun recoveryUsesDedicatedVerificationAndAuthenticatedPasswordPut() = runTest {
        val paths = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                paths += request.url.encodedPath
                val body = (request.body as TextContent).text
                when {
                    request.url.encodedPath.endsWith("recover") -> {
                        assertNull(request.headers["Authorization"])
                        assertTrue(body.contains("reader@example.test"))
                        respond("{}")
                    }

                    request.url.encodedPath.endsWith("verify") -> {
                        assertTrue(body.contains("recovery"))
                        assertTrue(body.contains("012345"))
                        assertFalse(body.contains("signup"))
                        respond(
                            """{"access_token":"access","refresh_token":"refresh","expires_in":3600,"user":{"id":"reader","email":"reader@example.test"}}""",
                        )
                    }

                    else -> {
                        assertEquals(HttpMethod.Put, request.method)
                        assertEquals("Bearer access", request.headers["Authorization"])
                        assertTrue(body.contains(" new password "))
                        respond("""{"id":"reader","email":"reader@example.test"}""")
                    }
                }
            },
        ) { configureAuthClient() }
        val remote = SupabaseAuthClient(client, AuthConfiguration("https://example.test", "sb_publishable_fixture"))

        assertIs<AuthResponse.Success<*>>(remote.requestRecovery("reader@example.test"))
        assertIs<AuthResponse.Success<*>>(remote.verifyRecovery("reader@example.test", "012345"))
        assertIs<AuthResponse.Success<*>>(remote.resetPassword("access", " new password "))
        assertNull(client.pluginOrNull(Logging))
        assertEquals(listOf("/auth/v1/recover", "/auth/v1/verify", "/auth/v1/user"), paths)
        client.close()
    }

    @Test
    fun unknownRecoveryAccountUsesNeutralAcknowledgement() = runTest {
        val client = HttpClient(
            MockEngine {
                respond("""{"code":"user_not_found"}""", HttpStatusCode.NotFound)
            },
        ) { configureAuthClient() }
        val remote = SupabaseAuthClient(client, AuthConfiguration("https://example.test", "sb_publishable_fixture"))
        assertIs<AuthResponse.Success<*>>(remote.requestRecovery("unknown@example.test"))
        client.close()
    }

    @Test
    fun absentConfigurationNeverSendsRequest() = runTest {
        var requested = false
        val client = HttpClient(
            MockEngine {
                requested = true
                respond("{}")
            },
        ) { configureAuthClient() }
        val remote = SupabaseAuthClient(client, AuthConfiguration("", ""))

        assertEquals(AuthResponse.Failed(AuthFailure.NOT_CONFIGURED), remote.signIn("reader@example.test", "password"))
        assertFalse(requested)
        client.close()
    }
}
