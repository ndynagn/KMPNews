package com.ndynagn.kmp.news.data.news

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class NewsResponseDto(
    val status: String,
    val totalResults: Long,
    val results: List<ArticleResponseDto>,
    val nextPage: String? = null,
)

@Serializable
internal data class ArticleResponseDto(
    @SerialName("article_id") val id: String,
    val title: String? = null,
    val link: String? = null,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    val pubDate: String? = null,
    val pubDateTZ: String? = null,
)
