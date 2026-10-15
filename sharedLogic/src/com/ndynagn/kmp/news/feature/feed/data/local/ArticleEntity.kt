package com.ndynagn.kmp.news.feature.feed.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ndynagn.kmp.news.feature.feed.domain.Article

@Entity(tableName = "articles")
internal data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String?,
    val url: String?,
    val description: String?,
    val imageUrl: String?,
    val sourceId: String?,
    val sourceName: String?,
    val publishedAtEpochMilliseconds: Long?,
    val position: Int,
    val feedId: Int = 1,
) {
    fun toDomain(): Article = Article(
        id = id,
        title = title,
        url = url,
        summary = description,
        imageUrl = imageUrl,
        sourceId = sourceId,
        sourceName = sourceName,
        publishedAtEpochMilliseconds = publishedAtEpochMilliseconds,
    )
}

internal fun Article.toEntity(position: Int): ArticleEntity = ArticleEntity(
    id = id,
    title = title,
    url = url,
    description = summary,
    imageUrl = imageUrl,
    sourceId = sourceId,
    sourceName = sourceName,
    publishedAtEpochMilliseconds = publishedAtEpochMilliseconds,
    position = position,
)
