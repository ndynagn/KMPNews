package com.ndynagn.kmp.news.feature.feed.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_metadata")
internal data class FeedMetadataEntity(@PrimaryKey val id: Int = 1, val refreshedAt: Long, val nextPage: String?)
