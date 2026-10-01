package com.ndynagn.kmp.news.feature.auth.data

import com.ndynagn.kmp.news.feature.auth.di.AuthConfiguration
import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal val authJson = Json { ignoreUnknownKeys = true }

internal class SupabaseAuthClient(private val httpClient: HttpClient, private val configuration: AuthConfiguration) :
    AuthRemoteSource {
    override suspend fun signIn(email: String, password: String): AuthResponse<AuthTokenDto> =
        token("token?grant_type=password", mapOf("email" to email, "password" to password))

    override suspend fun register(email: String, password: String): AuthResponse<Unit> =
        unit("signup", mapOf("email" to email, "password" to password))

    override suspend fun confirm(email: String, code: String): AuthResponse<AuthTokenDto> =
        token("verify", mapOf("email" to email, "token" to code, "type" to "signup"))

    override suspend fun resend(email: String): AuthResponse<Unit> =
        unit("resend", mapOf("email" to email, "type" to "signup"))

    override suspend fun requestRecovery(email: String): AuthResponse<Unit> = unit("recover", mapOf("email" to email))

    override suspend fun verifyRecovery(email: String, code: String): AuthResponse<AuthTokenDto> =
        token("verify", mapOf("email" to email, "token" to code, "type" to "recovery"))

    override suspend fun resetPassword(token: String, password: String): AuthResponse<AuthUserDto> =
        call("user", mapOf("password" to password), token, HttpMethod.Put) {
            authJson.decodeFromString<AuthUserDto>(it)
        }

    override suspend fun refresh(token: String): AuthResponse<AuthTokenDto> =
        token("token?grant_type=refresh_token", mapOf("refresh_token" to token))

    override suspend fun user(token: String): AuthResponse<AuthUserDto> =
        call("user", null, token) { authJson.decodeFromString<AuthUserDto>(it) }

    override suspend fun logout(token: String): AuthResponse<Unit> = call("logout?scope=local", emptyMap(), token) { }

    private suspend fun token(path: String, fields: Map<String, String>): AuthResponse<AuthTokenDto> =
        call(path, fields, null) { authJson.decodeFromString<AuthTokenDto>(it) }

    private suspend fun unit(path: String, fields: Map<String, String>): AuthResponse<Unit> =
        call(path, fields, null) { }

    private suspend fun <T> call(
        path: String,
        fields: Map<String, String>?,
        token: String?,
        method: HttpMethod = if (fields == null) HttpMethod.Get else HttpMethod.Post,
        decode: (String) -> T,
    ): AuthResponse<T> {
        if (!configuration.isConfigured) return AuthResponse.Failed(AuthFailure.NOT_CONFIGURED)
        return try {
            val response = httpClient.request(configuration.baseUrl + path) {
                this.method = method
                header("apikey", configuration.publishableKey)
                if (token != null) bearerAuth(token)
                if (fields != null) {
                    contentType(ContentType.Application.Json)
                    setBody(authJson.encodeToString(fields))
                }
            }
            val body = response.bodyAsText()
            if (response.status.value in 200..299) {
                AuthResponse.Success(decode(body))
            } else {
                val code = runCatching {
                    val json = authJson.parseToJsonElement(body).jsonObject
                    (json["code"] ?: json["error_code"])?.jsonPrimitive?.content
                }.getOrNull()
                if (path == "signup" && code in setOf("user_already_exists", "email_exists")) {
                    // Keep the same confirmation acknowledgement for existing and new accounts.
                    return AuthResponse.Success(decode(body))
                }
                if (path == "recover" && code == "user_not_found") {
                    return AuthResponse.Success(decode(body))
                }
                val failure = when {
                    response.status.value == 429 -> AuthFailure.RATE_LIMITED

                    response.status.value >= 500 -> AuthFailure.SERVICE

                    code == "email_not_confirmed" -> AuthFailure.EMAIL_UNCONFIRMED

                    code == "weak_password" -> AuthFailure.WEAK_PASSWORD

                    code in setOf("otp_expired", "otp_disabled") || path == "verify" -> AuthFailure.INVALID_CODE

                    code in
                        setOf(
                            "refresh_token_not_found",
                            "refresh_token_already_used",
                            "session_not_found",
                            "session_expired",
                        ) -> AuthFailure.SESSION_EXPIRED

                    path == "user" && response.status.value == 401 -> AuthFailure.SESSION_EXPIRED

                    code == "invalid_credentials" -> AuthFailure.INVALID_CREDENTIALS

                    else -> AuthFailure.SERVICE
                }
                AuthResponse.Failed(failure)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SerializationException) {
            AuthResponse.Failed(AuthFailure.SERVICE)
        } catch (_: Exception) {
            AuthResponse.Failed(AuthFailure.NETWORK)
        }
    }
}
