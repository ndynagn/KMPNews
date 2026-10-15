package com.ndynagn.kmp.news.data.news

import com.ndynagn.kmp.news.feature.feed.domain.Article
import kotlin.time.Instant

internal fun ArticleResponseDto.toDomain(): Article {
    val publishedAt = pubDate?.let { raw ->
        require(pubDateTZ == null || pubDateTZ == "UTC")

        // timezone=UTC is explicit in every request. Invalid dates reject the page.
        require(raw.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
        Instant.parse(raw.replace(' ', 'T') + "Z").toEpochMilliseconds()
    }

    return Article(
        id = id,
        title = title,
        url = link,
        summary = description,
        imageUrl = imageUrl,
        sourceId = sourceId,
        sourceName = sourceName,
        publishedAtEpochMilliseconds = publishedAt,
    )
}
