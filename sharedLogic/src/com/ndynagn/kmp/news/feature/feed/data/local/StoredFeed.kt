package com.ndynagn.kmp.news.feature.feed.data.local

import androidx.room.Embedded
import androidx.room.Relation

internal data class StoredFeed(
    @Embedded val metadata: FeedMetadataEntity,
    @Relation(parentColumn = "id", entityColumn = "feedId") val articles: List<ArticleEntity>,
)
