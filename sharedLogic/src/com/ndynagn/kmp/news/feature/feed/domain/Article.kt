package com.ndynagn.kmp.news.feature.feed.domain

/** Provider metadata only; nullable values are not replaced with invented content. */
data class Article(
    val id: String,
    val title: String? = null,
    val url: String? = null,
    val summary: String? = null,
    val imageUrl: String? = null,
    val sourceId: String? = null,
    val sourceName: String? = null,
    val publishedAtEpochMilliseconds: Long? = null,
)
