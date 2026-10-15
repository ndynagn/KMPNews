package com.ndynagn.kmp.news.feature.favorites.data

import com.ndynagn.kmp.news.feature.favorites.domain.FavoriteArticle
import com.ndynagn.kmp.news.feature.feed.domain.Article
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

internal val favoritesJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class FavoriteDto(
    @SerialName("user_id") val userId: String,
    @SerialName("article_id") val articleId: String,
    @SerialName("added_at") val addedAt: String,
    val title: String? = null,
    val url: String? = null,
    val summary: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("published_at_epoch_milliseconds") val publishedAt: Long? = null,
) {
    fun toDomain(): FavoriteArticle = FavoriteArticle(
        Article(articleId, title, url, summary, imageUrl, sourceId, sourceName, publishedAt),
        Instant.parse(addedAt).toEpochMilliseconds(),
    )
}

@Serializable
internal data class FavoriteInsertDto(
    @SerialName("user_id") val userId: String,
    @SerialName("article_id") val articleId: String,
    val title: String?,
    val url: String?,
    val summary: String?,
    @SerialName("image_url") val imageUrl: String?,
    @SerialName("source_id") val sourceId: String?,
    @SerialName("source_name") val sourceName: String?,
    @SerialName("published_at_epoch_milliseconds") val publishedAt: Long?,
)
