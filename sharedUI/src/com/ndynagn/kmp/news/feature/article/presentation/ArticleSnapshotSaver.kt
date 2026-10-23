package com.ndynagn.kmp.news.feature.article.presentation

import androidx.compose.runtime.saveable.Saver
import com.ndynagn.kmp.news.feature.feed.domain.Article

/** Saves the complete selected snapshot, without making the domain model Android-serializable. */
internal val articleSnapshotSaver = Saver<Article?, Any>(
    save = { article ->
        article?.let {
            arrayListOf(
                it.id,
                it.title,
                it.url,
                it.summary,
                it.imageUrl,
                it.sourceId,
                it.sourceName,
                it.publishedAtEpochMilliseconds,
            )
        }
    },
    restore = { saved ->
        val fields = saved as List<*>

        Article(
            id = fields[0] as String,
            title = fields[1] as String?,
            url = fields[2] as String?,
            summary = fields[3] as String?,
            imageUrl = fields[4] as String?,
            sourceId = fields[5] as String?,
            sourceName = fields[6] as String?,
            publishedAtEpochMilliseconds = fields[7] as Long?,
        )
    },
)
