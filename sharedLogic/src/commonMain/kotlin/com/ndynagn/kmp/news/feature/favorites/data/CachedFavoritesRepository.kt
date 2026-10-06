package com.ndynagn.kmp.news.feature.favorites.data

import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import com.ndynagn.kmp.news.feature.favorites.data.local.FavoritesStore
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesFailure
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesMembershipResult
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesReadResult
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesRepository
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesUpdateResult
import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.network.AccountCredentials
import com.ndynagn.kmp.news.network.AccountIdentity
import com.ndynagn.kmp.news.network.AccountSessionAccess
import com.ndynagn.kmp.news.network.CredentialsResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex

internal class CachedFavoritesRepository(
    private val sessions: AccountSessionAccess,
    private val store: FavoritesStore,
    private val remote: FavoritesRemoteSource,
) : FavoritesRepository {
    private val operationMutex = Mutex()

    override suspend fun membership(articleIds: List<String>): FavoritesMembershipResult {
        val ids = articleIds.distinct()
        if (ids.any(String::isEmpty)) {
            return FavoritesMembershipResult.Failed(FavoritesFailure.INVALID_INPUT)
        }
        val account = sessions.account.value
            ?: return FavoritesMembershipResult.Failed(FavoritesFailure.AUTH_REQUIRED)
        if (ids.isEmpty()) return FavoritesMembershipResult.Snapshot(emptySet())

        val saved = mutableSetOf<String>()
        for (batch in ids.chunked(200)) {
            when (val result = authorized(account) { remote.membership(it, batch) }) {
                is FavoritesResponse.Success -> saved.addAll(result.value)
                is FavoritesResponse.Failed -> return FavoritesMembershipResult.Failed(result.failure)
            }
        }
        return FavoritesMembershipResult.Snapshot(saved)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFavorites(): Flow<FavoritesReadResult> = sessions.account.flatMapLatest { account ->
        if (account == null) {
            flowOf(FavoritesReadResult.SignedOut)
        } else {
            flow { emitAll(store.observe(account.userId)) }.map { cache ->
                if (sessions.account.value != account) {
                    FavoritesReadResult.SignedOut
                } else {
                    FavoritesReadResult.Snapshot(
                        account.userId,
                        cache.entries.map { it.toDomain() },
                        cache.cursor != null,
                        cache.isInitialized,
                    )
                }
            }.catch { error ->
                if (error is CancellationException) throw error

                emit(FavoritesReadResult.Failed(FavoritesFailure.STORAGE))
            }
        }
    }.buffer(0)

    override suspend fun refresh(): FavoritesUpdateResult = operate { account ->
        page(account, cursor = null, replace = true)
    }

    override suspend fun loadNextPage(): FavoritesUpdateResult = operate { account ->
        val cache = store.read(account.userId)
        if (!cache.isInitialized) return@operate page(account, cursor = null, replace = true)
        val cursor = cache.cursor ?: return@operate FavoritesUpdateResult.NoMorePages

        page(account, cursor, replace = false)
    }

    override suspend fun add(article: Article): FavoritesUpdateResult {
        if (article.id.isEmpty()) return FavoritesUpdateResult.Failed(FavoritesFailure.INVALID_INPUT)

        return operate(write = true) { account ->
            when (val result = authorized(account) { remote.add(it, article) }) {
                is FavoritesResponse.Failed -> result.toUpdate(remoteMayHaveChanged = true)

                is FavoritesResponse.Success -> when (val saved = authorized(account) { remote.find(it, article.id) }) {
                    is FavoritesResponse.Failed -> saved.toUpdate(remoteMayHaveChanged = true)
                    is FavoritesResponse.Success -> commit(account, write = true) { store.save(saved.value) }
                }
            }
        }
    }

    override suspend fun remove(articleId: String): FavoritesUpdateResult {
        if (articleId.isEmpty()) return FavoritesUpdateResult.Failed(FavoritesFailure.INVALID_INPUT)

        return operate(write = true) { account ->
            when (val result = authorized(account) { remote.remove(it, articleId) }) {
                is FavoritesResponse.Failed -> result.toUpdate(remoteMayHaveChanged = true)

                is FavoritesResponse.Success -> commit(account, write = true) {
                    store.remove(account.userId, articleId)
                }
            }
        }
    }

    private suspend fun page(
        account: AccountIdentity,
        cursor: FavoritesCursor?,
        replace: Boolean,
    ): FavoritesUpdateResult = when (val result = authorized(account) { remote.fetch(it, cursor) }) {
        is FavoritesResponse.Failed -> result.toUpdate()
        is FavoritesResponse.Success -> commit(account) { store.writePage(account.userId, result.value, replace) }
    }

    private suspend fun commit(
        account: AccountIdentity,
        write: Boolean = false,
        block: suspend () -> Unit,
    ): FavoritesUpdateResult = if (sessions.commitIfCurrent(account, block)) {
        FavoritesUpdateResult.Updated
    } else {
        FavoritesUpdateResult.Failed(FavoritesFailure.SESSION_CHANGED, remoteMayHaveChanged = write)
    }

    private suspend fun operate(
        write: Boolean = false,
        block: suspend (AccountIdentity) -> FavoritesUpdateResult,
    ): FavoritesUpdateResult {
        val account = sessions.account.value ?: return FavoritesUpdateResult.Failed(FavoritesFailure.AUTH_REQUIRED)
        if (!operationMutex.tryLock()) return FavoritesUpdateResult.AlreadyRunning

        return try {
            block(account)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            FavoritesUpdateResult.Failed(FavoritesFailure.STORAGE, remoteMayHaveChanged = write)
        } finally {
            operationMutex.unlock()
        }
    }

    private suspend fun <T> authorized(
        account: AccountIdentity,
        request: suspend (AccountCredentials) -> FavoritesResponse<T>,
    ): FavoritesResponse<T> {
        var rejectedToken: String? = null
        repeat(2) {
            val credentials = when (val result = sessions.credentials(account, rejectedToken)) {
                is CredentialsResult.Failed -> return FavoritesResponse.Failed(result.failure.toFavoritesFailure())
                is CredentialsResult.Ready -> result.credentials
            }
            currentCoroutineContext().ensureActive()
            if (sessions.account.value != account) return FavoritesResponse.Failed(FavoritesFailure.SESSION_CHANGED)

            val result = request(credentials)
            currentCoroutineContext().ensureActive()
            if (sessions.account.value != account) return FavoritesResponse.Failed(FavoritesFailure.SESSION_CHANGED)
            if (result !is FavoritesResponse.Failed || result.failure != FavoritesFailure.AUTH_REQUIRED) return result

            if (it == 1) sessions.reject(account, credentials.accessToken)
            rejectedToken = credentials.accessToken
        }

        return FavoritesResponse.Failed(FavoritesFailure.AUTH_REQUIRED)
    }

    private fun FavoritesResponse.Failed.toUpdate(remoteMayHaveChanged: Boolean = false) =
        FavoritesUpdateResult.Failed(failure, remoteMayHaveChanged)

    private fun AuthFailure.toFavoritesFailure(): FavoritesFailure = when (this) {
        AuthFailure.NETWORK -> FavoritesFailure.NETWORK
        AuthFailure.STORAGE -> FavoritesFailure.STORAGE
        AuthFailure.RATE_LIMITED -> FavoritesFailure.RATE_LIMITED
        AuthFailure.NOT_CONFIGURED -> FavoritesFailure.NOT_CONFIGURED
        AuthFailure.SERVICE -> FavoritesFailure.SERVICE
        else -> FavoritesFailure.AUTH_REQUIRED
    }
}
