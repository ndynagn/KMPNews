package com.ndynagn.kmp.news.feature.favorites.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "favorites", primaryKeys = ["userId", "articleId"], indices = [Index("userId")])
internal data class FavoriteEntity(val userId: String, val articleId: String, val payload: String)

@Entity(tableName = "favorites_metadata")
internal data class FavoritesMetadataEntity(
    @PrimaryKey val userId: String,
    val cursorTime: String? = null,
    val cursorId: String? = null,
    val isInitialized: Boolean = false,
)

internal data class StoredFavorites(
    @Embedded val metadata: FavoritesMetadataEntity,
    @Relation(parentColumn = "userId", entityColumn = "userId") val entries: List<FavoriteEntity>,
)
