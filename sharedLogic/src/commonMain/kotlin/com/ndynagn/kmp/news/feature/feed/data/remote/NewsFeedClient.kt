package com.ndynagn.kmp.news.feature.feed.data.remote

import com.ndynagn.kmp.news.feature.feed.di.FeedApiConfiguration
import com.ndynagn.kmp.news.feature.feed.domain.FeedFailure
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

internal class NewsFeedClient(private val httpClient: HttpClient, private val configuration: FeedApiConfiguration) :
    NewsRemoteSource {
    override suspend fun fetch(page: String?): PageResult {
        if (!configuration.isConfigured) return PageResult.Failure(FeedFailure.ACCESS_DENIED)

        var statusCode: Int? = null

        return try {
            val response = httpClient.get("news-feed") {
                header("apikey", configuration.publishableKey)
                if (page != null) parameter("page", page)
            }

            statusCode = response.status.value

            val root = response.body<JsonObject>()

            decode(response.status.value, root)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: HttpRequestTimeoutException) {
            return PageResult.Failure(FeedFailure.TIMEOUT)
        } catch (_: ConnectTimeoutException) {
            return PageResult.Failure(FeedFailure.TIMEOUT)
        } catch (_: SocketTimeoutException) {
            return PageResult.Failure(FeedFailure.TIMEOUT)
        } catch (_: JsonConvertException) {
            PageResult.Failure(statusCode?.let(::httpFailure) ?: FeedFailure.INVALID_RESPONSE)
        } catch (_: NoTransformationFoundException) {
            PageResult.Failure(statusCode?.let(::httpFailure) ?: FeedFailure.INVALID_RESPONSE)
        } catch (_: Exception) {
            return PageResult.Failure(FeedFailure.NETWORK)
        }
    }

    private fun decode(statusCode: Int, root: JsonObject): PageResult = try {
        val status = (root["status"] as? JsonPrimitive)?.contentOrNull

        if (statusCode !in 200..299 || status == "error") {
            val details = when (val results = root["results"]) {
                is JsonObject -> results
                is JsonArray -> results.firstOrNull() as? JsonObject
                else -> null
            }
            val code = (details?.get("code") as? JsonPrimitive)?.contentOrNull?.trim()
            val failure = when (code) {
                "UpstreamTimeout" -> FeedFailure.TIMEOUT
                "InvalidRequest" -> FeedFailure.INVALID_REQUEST
                "InvalidResponse" -> FeedFailure.INVALID_RESPONSE
                "ApiLimitExceeded" -> FeedFailure.QUOTA_EXCEEDED
                "RateLimitExceeded", "TooManyRequests" -> FeedFailure.RATE_LIMITED
                "Unauthorized", "AccessDenied" -> FeedFailure.ACCESS_DENIED
                "ServerError", "ServiceUnavailable" -> FeedFailure.SERVER_UNAVAILABLE
                else -> httpFailure(statusCode) ?: FeedFailure.INVALID_RESPONSE
            }

            PageResult.Failure(failure)
        } else if (status == "success") {
            val dto = feedJson.decodeFromJsonElement<NewsResponseDto>(root)

            PageResult.Success(NewsPage(dto.results.map { it.toDomain() }, dto.nextPage))
        } else {
            PageResult.Failure(FeedFailure.INVALID_RESPONSE)
        }
    } catch (_: IllegalArgumentException) {
        PageResult.Failure(httpFailure(statusCode) ?: FeedFailure.INVALID_RESPONSE)
    }

    private fun httpFailure(code: Int): FeedFailure? = when (code) {
        401, 403 -> FeedFailure.ACCESS_DENIED
        400, 404, 422 -> FeedFailure.INVALID_REQUEST
        429 -> FeedFailure.RATE_LIMITED
        in 500..599 -> FeedFailure.SERVER_UNAVAILABLE
        else -> null
    }
}
