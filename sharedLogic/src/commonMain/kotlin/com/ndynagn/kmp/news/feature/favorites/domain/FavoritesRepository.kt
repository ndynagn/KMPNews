package com.ndynagn.kmp.news.feature.favorites.domain

import com.ndynagn.kmp.news.feature.feed.domain.Article
import kotlinx.coroutines.flow.Flow

/** An independent article snapshot; save time is server-assigned UTC Unix milliseconds. */
data class FavoriteArticle(val article: Article, val addedAtEpochMilliseconds: Long)

/** Expected failures contain no provider messages or credentials. */
enum class FavoritesFailure {
    AUTH_REQUIRED,
    SESSION_CHANGED,
    NETWORK,
    TIMEOUT,
    ACCESS_DENIED,
    RATE_LIMITED,
    SERVICE,
    INVALID_RESPONSE,
    INVALID_INPUT,
    STORAGE,
    NOT_CONFIGURED,
}

/** Room is the display source. Unavailable storage is distinct from an empty list. */
sealed interface FavoritesReadResult {
    data object SignedOut : FavoritesReadResult
    data class Snapshot(
        val userId: String,
        val articles: List<FavoriteArticle>,
        val hasMore: Boolean,
        val isInitialized: Boolean,
    ) : FavoritesReadResult
    data class Failed(val failure: FavoritesFailure) : FavoritesReadResult
}

/** A failed write may already have reached the server; refresh reconciles an uncertain outcome. */
sealed interface FavoritesUpdateResult {
    data object Updated : FavoritesUpdateResult
    data object AlreadyRunning : FavoritesUpdateResult
    data object NoMorePages : FavoritesUpdateResult
    data class Failed(val failure: FavoritesFailure, val remoteMayHaveChanged: Boolean = false) : FavoritesUpdateResult
}

/** Membership for requested article IDs, independent of the loaded favorites page. */
sealed interface FavoritesMembershipResult {
    data class Snapshot(val articleIds: Set<String>) : FavoritesMembershipResult
    data class Failed(val failure: FavoritesFailure) : FavoritesMembershipResult
}

/**
 * Account-scoped saved articles. Callers own coroutines; cancellation propagates.
 * Mutations require network; no offline write queue. Concurrent writes/page requests return AlreadyRunning.
 * Membership requests are independent reads and may run concurrently.
 * Logout/account changes hide the previous cache and invalidate its pending commits.
 */
interface FavoritesRepository {
    /**
     * Deduplicates IDs and reads batches of at most 200 under one captured account identity.
     * Failure, cancellation or account change discards all batches; no partial snapshot is returned.
     * Concurrent server edits across batches are not an atomic snapshot. Empty IDs are invalid.
     */
    suspend fun membership(articleIds: List<String>): FavoritesMembershipResult

    /** Observes only local data and account changes; never starts a network request. */
    fun observeFavorites(): Flow<FavoritesReadResult>

    /** Replaces the loaded window with the first server page atomically; errors retain cache and cursor. */
    suspend fun refresh(): FavoritesUpdateResult

    /** Appends after the stored opaque ordering pair; errors preserve the previous cursor. */
    suspend fun loadNextPage(): FavoritesUpdateResult

    /** Saves idempotently, then reads the authoritative snapshot/time before committing locally. */
    suspend fun add(article: Article): FavoritesUpdateResult

    /** Deletes idempotently on the server before removing the local snapshot. */
    suspend fun remove(articleId: String): FavoritesUpdateResult
}
