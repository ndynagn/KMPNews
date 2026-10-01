package com.ndynagn.kmp.news.feature.favorites.data

import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesFailure
import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.network.AccountCredentials
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlin.time.Instant

internal class SupabaseFavoritesClient(
    private val client: HttpClient,
    private val projectUrl: String,
    private val publishableKey: String,
    private val isConfigured: Boolean,
) : FavoritesRemoteSource {
    override suspend fun fetch(
        credentials: AccountCredentials,
        cursor: FavoritesCursor?,
    ): FavoritesResponse<FavoritesPage> = call(
        credentials,
        HttpMethod.Get,
        buildMap {
            put("select", FIELDS)
            put("order", "added_at.desc,article_id.desc")
            put("limit", PAGE_SIZE.toString())
            if (cursor != null) {
                val time = quote(cursor.addedAt)
                put("or", "(added_at.lt.$time,and(added_at.eq.$time,article_id.lt.${quote(cursor.articleId)}))")
            }
        },
    ) { body ->
        val rows = decodeRows(body, credentials)
        require(rows.size <= PAGE_SIZE && rows.map { it.articleId }.distinct().size == rows.size)
        val next = if (rows.size == PAGE_SIZE) rows.last().let { FavoritesCursor(it.addedAt, it.articleId) } else null
        require(next == null || next != cursor)

        FavoritesPage(rows, next)
    }

    override suspend fun find(credentials: AccountCredentials, articleId: String): FavoritesResponse<FavoriteDto> =
        call(credentials, HttpMethod.Get, mapOf("select" to FIELDS, "article_id" to "eq.${quote(articleId)}")) {
            decodeRows(it, credentials).single().also { row -> require(row.articleId == articleId) }
        }

    override suspend fun add(credentials: AccountCredentials, article: Article): FavoritesResponse<Unit> = call(
        credentials,
        HttpMethod.Post,
        mapOf("on_conflict" to "user_id,article_id"),
        favoritesJson.encodeToString(
            FavoriteInsertDto(
                userId = credentials.identity.userId,
                articleId = article.id,
                title = article.title,
                url = article.url,
                summary = article.summary,
                imageUrl = article.imageUrl,
                sourceId = article.sourceId,
                sourceName = article.sourceName,
                publishedAt = article.publishedAtEpochMilliseconds,
            ),
        ),
    ) { }

    override suspend fun remove(credentials: AccountCredentials, articleId: String): FavoritesResponse<Unit> =
        call(credentials, HttpMethod.Delete, mapOf("article_id" to "eq.${quote(articleId)}")) { }

    private fun decodeRows(body: String, credentials: AccountCredentials): List<FavoriteDto> =
        favoritesJson.decodeFromString<List<FavoriteDto>>(body).onEach {
            require(it.userId == credentials.identity.userId && it.articleId.isNotEmpty())
            Instant.parse(it.addedAt)
        }

    private suspend fun <T> call(
        credentials: AccountCredentials,
        method: HttpMethod,
        parameters: Map<String, String>,
        body: String? = null,
        decode: (String) -> T,
    ): FavoritesResponse<T> {
        if (!isConfigured) return FavoritesResponse.Failed(FavoritesFailure.NOT_CONFIGURED)

        return try {
            val response = client.request(projectUrl.trimEnd('/') + "/rest/v1/user_favorites") {
                this.method = method
                header("apikey", publishableKey)
                bearerAuth(credentials.accessToken)
                parameter("user_id", "eq.${quote(credentials.identity.userId)}")
                parameters.forEach { (key, value) -> parameter(key, value) }
                if (method == HttpMethod.Post) header("Prefer", "resolution=ignore-duplicates,return=minimal")
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }

            when (response.status.value) {
                in 200..299 -> FavoritesResponse.Success(decode(response.bodyAsText()))
                401 -> FavoritesResponse.Failed(FavoritesFailure.AUTH_REQUIRED)
                403 -> FavoritesResponse.Failed(FavoritesFailure.ACCESS_DENIED)
                429 -> FavoritesResponse.Failed(FavoritesFailure.RATE_LIMITED)
                in 500..599 -> FavoritesResponse.Failed(FavoritesFailure.SERVICE)
                else -> FavoritesResponse.Failed(FavoritesFailure.INVALID_RESPONSE)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: HttpRequestTimeoutException) {
            FavoritesResponse.Failed(FavoritesFailure.TIMEOUT)
        } catch (_: ConnectTimeoutException) {
            FavoritesResponse.Failed(FavoritesFailure.TIMEOUT)
        } catch (_: SocketTimeoutException) {
            FavoritesResponse.Failed(FavoritesFailure.TIMEOUT)
        } catch (_: SerializationException) {
            FavoritesResponse.Failed(FavoritesFailure.INVALID_RESPONSE)
        } catch (_: IllegalArgumentException) {
            FavoritesResponse.Failed(FavoritesFailure.INVALID_RESPONSE)
        } catch (_: NoSuchElementException) {
            FavoritesResponse.Failed(FavoritesFailure.INVALID_RESPONSE)
        } catch (_: Exception) {
            FavoritesResponse.Failed(FavoritesFailure.NETWORK)
        }
    }

    // PostgREST grammar escaping is separate from Ktor's URL encoding.
    private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private companion object {
        const val PAGE_SIZE = 50
        const val FIELDS = "user_id,article_id,added_at,title,url,summary,image_url,source_id,source_name," +
            "published_at_epoch_milliseconds"
    }
}
