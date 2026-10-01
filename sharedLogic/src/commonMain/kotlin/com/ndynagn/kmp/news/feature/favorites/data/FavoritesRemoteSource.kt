package com.ndynagn.kmp.news.feature.favorites.data

import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesFailure
import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.network.AccountCredentials

internal data class FavoritesCursor(val addedAt: String, val articleId: String)
internal data class FavoritesPage(val entries: List<FavoriteDto>, val next: FavoritesCursor?)

internal sealed interface FavoritesResponse<out T> {
    data class Success<T>(val value: T) : FavoritesResponse<T>
    data class Failed(val failure: FavoritesFailure) : FavoritesResponse<Nothing>
}

internal interface FavoritesRemoteSource {
    suspend fun fetch(credentials: AccountCredentials, cursor: FavoritesCursor?): FavoritesResponse<FavoritesPage>
    suspend fun find(credentials: AccountCredentials, articleId: String): FavoritesResponse<FavoriteDto>
    suspend fun add(credentials: AccountCredentials, article: Article): FavoritesResponse<Unit>
    suspend fun remove(credentials: AccountCredentials, articleId: String): FavoritesResponse<Unit>
}
