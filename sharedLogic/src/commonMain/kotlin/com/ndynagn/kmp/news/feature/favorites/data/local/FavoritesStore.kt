package com.ndynagn.kmp.news.feature.favorites.data.local

import com.ndynagn.kmp.news.feature.favorites.data.FavoriteDto
import com.ndynagn.kmp.news.feature.favorites.data.FavoritesCursor
import com.ndynagn.kmp.news.feature.favorites.data.FavoritesPage
import kotlinx.coroutines.flow.Flow

internal data class CachedFavorites(
    val entries: List<FavoriteDto> = emptyList(),
    val cursor: FavoritesCursor? = null,
    val isInitialized: Boolean = false,
)

internal interface FavoritesStore {
    fun observe(userId: String): Flow<CachedFavorites>
    suspend fun read(userId: String): CachedFavorites
    suspend fun writePage(userId: String, page: FavoritesPage, replace: Boolean)
    suspend fun save(entry: FavoriteDto)
    suspend fun remove(userId: String, articleId: String)
}
