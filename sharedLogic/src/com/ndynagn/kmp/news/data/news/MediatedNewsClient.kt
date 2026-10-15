package com.ndynagn.kmp.news.data.news

import com.ndynagn.kmp.news.network.NewsApiConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

internal class MediatedNewsClient(private val httpClient: HttpClient, private val configuration: NewsApiConfiguration) {
    suspend fun fetch(query: String?, page: String?): NewsResponse {
        if (!configuration.isConfigured) return NewsResponse.Failure(NewsRequestFailure.ACCESS_DENIED)

        var statusCode: Int? = null

        return try {
            val response = httpClient.get("news-feed") {
                header("apikey", configuration.publishableKey)
                if (page != null) parameter("page", page)
                if (query != null) parameter("q", query)
            }

            statusCode = response.status.value

            val root = response.body<JsonObject>()

            decode(response.status.value, root)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: HttpRequestTimeoutException) {
            return NewsResponse.Failure(NewsRequestFailure.TIMEOUT)
        } catch (_: ConnectTimeoutException) {
            return NewsResponse.Failure(NewsRequestFailure.TIMEOUT)
        } catch (_: SocketTimeoutException) {
            return NewsResponse.Failure(NewsRequestFailure.TIMEOUT)
        } catch (_: JsonConvertException) {
            NewsResponse.Failure(statusCode?.let(::httpFailure) ?: NewsRequestFailure.INVALID_RESPONSE)
        } catch (_: NoTransformationFoundException) {
            NewsResponse.Failure(statusCode?.let(::httpFailure) ?: NewsRequestFailure.INVALID_RESPONSE)
        } catch (_: Exception) {
            return NewsResponse.Failure(NewsRequestFailure.NETWORK)
        }
    }

    private fun decode(statusCode: Int, root: JsonObject): NewsResponse = try {
        val status = (root["status"] as? JsonPrimitive)?.contentOrNull

        if (statusCode !in 200..299 || status == "error") {
            val details = when (val results = root["results"]) {
                is JsonObject -> results
                is JsonArray -> results.firstOrNull() as? JsonObject
                else -> null
            }
            val code = (details?.get("code") as? JsonPrimitive)?.contentOrNull?.trim()
            val failure = when (code) {
                "UpstreamTimeout" -> NewsRequestFailure.TIMEOUT
                "InvalidRequest" -> NewsRequestFailure.INVALID_REQUEST
                "InvalidResponse" -> NewsRequestFailure.INVALID_RESPONSE
                "ApiLimitExceeded" -> NewsRequestFailure.QUOTA_EXCEEDED
                "RateLimitExceeded", "TooManyRequests" -> NewsRequestFailure.RATE_LIMITED
                "Unauthorized", "AccessDenied" -> NewsRequestFailure.ACCESS_DENIED
                "ServerError", "ServiceUnavailable" -> NewsRequestFailure.SERVER_UNAVAILABLE
                else -> httpFailure(statusCode) ?: NewsRequestFailure.INVALID_RESPONSE
            }

            NewsResponse.Failure(failure)
        } else if (status == "success") {
            val dto = newsJson.decodeFromJsonElement<NewsResponseDto>(root)

            NewsResponse.Success(RemoteNewsPage(dto.results.map { it.toDomain() }, dto.nextPage))
        } else {
            NewsResponse.Failure(NewsRequestFailure.INVALID_RESPONSE)
        }
    } catch (_: IllegalArgumentException) {
        NewsResponse.Failure(httpFailure(statusCode) ?: NewsRequestFailure.INVALID_RESPONSE)
    }

    private fun httpFailure(code: Int): NewsRequestFailure? = when (code) {
        401, 403 -> NewsRequestFailure.ACCESS_DENIED
        400, 404, 422 -> NewsRequestFailure.INVALID_REQUEST
        429 -> NewsRequestFailure.RATE_LIMITED
        in 500..599 -> NewsRequestFailure.SERVER_UNAVAILABLE
        else -> null
    }
}
