package com.ndynagn.kmp.news.feature.profile.data

import com.ndynagn.kmp.news.feature.profile.domain.ProfileFailure
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class SupabaseProfileClient(
    private val client: HttpClient,
    private val projectUrl: String,
    private val publishableKey: String,
    private val isConfigured: Boolean,
) : ProfileRemoteSource {
    override suspend fun fetch(token: String): ProfileResponse<ProfileUserDto> =
        call("auth/v1/user", token, HttpMethod.Get, decode = ::user)

    override suspend fun update(token: String, metadata: JsonObject): ProfileResponse<ProfileUserDto> =
        call("auth/v1/user", token, HttpMethod.Put, JsonObject(mapOf("data" to metadata)), decode = ::user)

    override suspend fun upload(token: String, path: String, jpeg: ByteArray): ProfileResponse<Unit> =
        call("storage/v1/object/avatars/$path", token, HttpMethod.Post, jpeg = jpeg) { }

    override suspend fun delete(token: String, paths: List<String>): ProfileResponse<Unit> = call(
        "storage/v1/object/avatars",
        token,
        HttpMethod.Delete,
        JsonObject(mapOf("prefixes" to JsonArray(paths.map(::JsonPrimitive)))),
    ) { }

    override suspend fun sign(token: String, path: String): ProfileResponse<String> = call(
        "storage/v1/object/sign/avatars/$path",
        token,
        HttpMethod.Post,
        JsonObject(mapOf("expiresIn" to JsonPrimitive(3600))),
    ) { body ->
        val signed = Json.parseToJsonElement(body).jsonObject.getValue("signedURL").jsonPrimitive.content

        require(signed.startsWith("/object/sign/avatars/") && !signed.contains(".."))

        projectUrl.trimEnd('/') + "/storage/v1" + signed
    }

    private fun user(body: String): ProfileUserDto {
        val json = Json.parseToJsonElement(body).jsonObject

        return ProfileUserDto(
            json.getValue("id").jsonPrimitive.content,
            json.getValue("email").jsonPrimitive.content,
            json["user_metadata"] as? JsonObject ?: JsonObject(emptyMap()),
        )
    }

    private suspend fun <T> call(
        path: String,
        token: String,
        method: HttpMethod,
        body: JsonObject? = null,
        jpeg: ByteArray? = null,
        decode: (String) -> T,
    ): ProfileResponse<T> {
        if (!isConfigured) return ProfileResponse.Failed(ProfileFailure.SERVICE)

        return try {
            val response = client.request(projectUrl.trimEnd('/') + "/" + path) {
                this.method = method
                bearerAuth(token)
                header("apikey", publishableKey)

                if (jpeg != null) {
                    contentType(ContentType.Image.JPEG)
                    setBody(jpeg)
                } else if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            }

            if (response.status.value in 200..299) {
                try {
                    ProfileResponse.Success(decode(response.bodyAsText()))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ProfileResponse.Failed(ProfileFailure.SERVICE)
                }
            } else {
                ProfileResponse.Failed(
                    when (response.status.value) {
                        401 -> ProfileFailure.SESSION_EXPIRED
                        429 -> ProfileFailure.RATE_LIMITED
                        413, 415 -> ProfileFailure.INVALID_PHOTO
                        else -> ProfileFailure.SERVICE
                    },
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ProfileResponse.Failed(ProfileFailure.NETWORK)
        }
    }
}
