package com.ndynagn.kmp.news.feature.favorites

import com.ndynagn.kmp.news.feature.favorites.data.CachedFavoritesRepository
import com.ndynagn.kmp.news.feature.favorites.data.FavoriteDto
import com.ndynagn.kmp.news.feature.favorites.data.FavoritesCursor
import com.ndynagn.kmp.news.feature.favorites.data.FavoritesPage
import com.ndynagn.kmp.news.feature.favorites.data.FavoritesRemoteSource
import com.ndynagn.kmp.news.feature.favorites.data.FavoritesResponse
import com.ndynagn.kmp.news.feature.favorites.data.local.CachedFavorites
import com.ndynagn.kmp.news.feature.favorites.data.local.FavoritesStore
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesFailure
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesMembershipResult
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesReadResult
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesUpdateResult
import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.network.AccountCredentials
import com.ndynagn.kmp.news.network.AccountIdentity
import com.ndynagn.kmp.news.network.AccountSessionAccess
import com.ndynagn.kmp.news.network.CredentialsResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesRepositoryTest {
    @Test
    fun membershipDoesNotInferAbsenceFromPartialCacheAndRejectsStaleAccounts() = runTest {
        val sessions = FakeAccountAccess()
        val store = FakeFavoritesStore()
        val remote = FakeFavoritesRemote().apply { page = FavoritesPage(listOf(favorite("remote-only")), null) }
        val repository = CachedFavoritesRepository(sessions, store, remote)

        assertEquals(
            FavoritesMembershipResult.Snapshot(setOf("remote-only")),
            repository.membership(listOf("remote-only", "absent")),
        )
        assertTrue(store.read("reader").entries.isEmpty())
        remote.gate = CompletableDeferred()
        val pending = async { repository.membership(listOf("remote-only")) }
        runCurrent()
        sessions.account.value = AccountIdentity("other", 2)
        remote.gate?.complete(Unit)
        assertEquals(FavoritesMembershipResult.Failed(FavoritesFailure.SESSION_CHANGED), pending.await())
    }

    @Test
    fun membershipRejectsOversizedRequestsWithoutTransport() = runTest {
        val remote = FakeFavoritesRemote()
        val repository = CachedFavoritesRepository(FakeAccountAccess(), FakeFavoritesStore(), remote)
        assertEquals(
            FavoritesMembershipResult.Failed(FavoritesFailure.INVALID_INPUT),
            repository.membership((0..200).map { "id-$it" }),
        )
        assertEquals(0, remote.requests)
    }

    @Test
    fun observationUsesCacheAndSwitchingAccountsNeverShowsPreviousRows() = runTest {
        val sessions = FakeAccountAccess()
        val store = FakeFavoritesStore()
        store.save(favorite())
        val remote = FakeFavoritesRemote()
        val repository = CachedFavoritesRepository(sessions, store, remote)
        val observed = mutableListOf<FavoritesReadResult>()
        val collector = launch { repository.observeFavorites().collect { observed += it } }
        runCurrent()

        assertEquals("one", assertIs<FavoritesReadResult.Snapshot>(observed.last()).articles.single().article.id)
        sessions.account.value = null
        runCurrent()
        assertEquals(FavoritesReadResult.SignedOut, observed.last())
        sessions.account.value = AccountIdentity("other", 2)
        runCurrent()
        assertTrue(assertIs<FavoritesReadResult.Snapshot>(observed.last()).articles.isEmpty())
        assertEquals(0, remote.requests)
        collector.cancelAndJoin()
    }

    @Test
    fun failedPagesPreserveCacheAndCursorWhileSuccessfulRefreshReplacesWindow() = runTest {
        val sessions = FakeAccountAccess()
        val store = FakeFavoritesStore()
        val cursor = FavoritesCursor(TIME, "one")
        store.writePage("reader", FavoritesPage(listOf(favorite()), cursor), true)
        val remote = FakeFavoritesRemote().apply { failure = FavoritesFailure.NETWORK }
        val repository = CachedFavoritesRepository(sessions, store, remote)
        val original = store.read("reader")

        assertIs<FavoritesUpdateResult.Failed>(repository.loadNextPage())
        assertEquals(cursor, remote.lastCursor)
        assertEquals(original, store.read("reader"))
        assertIs<FavoritesUpdateResult.Failed>(repository.refresh())
        assertEquals(original, store.read("reader"))
        remote.failure = null
        remote.page = FavoritesPage(listOf(favorite("two")), null)
        assertEquals(FavoritesUpdateResult.Updated, repository.loadNextPage())
        assertEquals(listOf("one", "two"), store.read("reader").entries.map { it.articleId })
        assertEquals(FavoritesUpdateResult.NoMorePages, repository.loadNextPage())
        repository.refresh()
        assertEquals(listOf("two"), store.read("reader").entries.map { it.articleId })
    }

    @Test
    fun delayedResponseCannotCommitAfterLogoutAndOverlappingOperationsDoNotRun() = runTest {
        val sessions = FakeAccountAccess()
        val store = FakeFavoritesStore()
        val remote = FakeFavoritesRemote().apply { gate = CompletableDeferred() }
        val repository = CachedFavoritesRepository(sessions, store, remote)
        val pending = async { repository.refresh() }
        runCurrent()

        assertEquals(FavoritesUpdateResult.AlreadyRunning, repository.remove("one"))
        sessions.account.value = AccountIdentity("reader", 2)
        remote.gate?.complete(Unit)
        assertEquals(
            FavoritesUpdateResult.Failed(FavoritesFailure.SESSION_CHANGED),
            pending.await(),
        )
        assertTrue(store.read("reader").entries.isEmpty())
    }

    @Test
    fun writesCommitAuthoritativeSnapshotOnlyAfterServerSuccessAndReportUncertainty() = runTest {
        val sessions = FakeAccountAccess()
        val store = FakeFavoritesStore()
        val remote = FakeFavoritesRemote()
        val repository = CachedFavoritesRepository(sessions, store, remote)

        repository.add(Article("one", title = "local candidate"))
        assertEquals("server title", store.read("reader").entries.single().title)
        remote.failure = FavoritesFailure.NETWORK
        val failed = assertIs<FavoritesUpdateResult.Failed>(repository.remove("one"))
        assertTrue(failed.remoteMayHaveChanged)
        assertEquals(1, store.read("reader").entries.size)
        remote.failure = null
        store.failWrites = true
        assertEquals(
            FavoritesUpdateResult.Failed(FavoritesFailure.STORAGE, true),
            repository.remove("one"),
        )
        store.failWrites = false
        assertEquals(FavoritesUpdateResult.Updated, repository.remove("one"))
        assertTrue(store.read("reader").entries.isEmpty())
    }

    @Test
    fun unauthorizedRetriesOnceButCancellationIsNotAStorageFailure() = runTest {
        val sessions = FakeAccountAccess()
        val store = FakeFavoritesStore()
        val remote = FakeFavoritesRemote().apply { rejectOld = true }
        val repository = CachedFavoritesRepository(sessions, store, remote)

        assertEquals(FavoritesUpdateResult.Updated, repository.refresh())
        assertEquals(2, remote.requests)
        assertEquals(1, sessions.refreshes)
        remote.failure = FavoritesFailure.AUTH_REQUIRED
        val result = assertIs<FavoritesUpdateResult.Failed>(repository.refresh())
        assertEquals(FavoritesFailure.AUTH_REQUIRED, result.failure)
        assertEquals(4, remote.requests)
        assertTrue(sessions.rejected)

        remote.failure = null
        remote.gate = CompletableDeferred()
        val pending = launch { repository.refresh() }
        runCurrent()
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        remote.gate = null
        assertEquals(FavoritesUpdateResult.Updated, repository.refresh())
    }

    @Test
    fun storageObservationFailureIsTypedAndGuestDoesNotRequestNetwork() = runTest {
        val sessions = FakeAccountAccess()
        val remote = FakeFavoritesRemote()
        val store = FakeFavoritesStore().apply { failReads = true }
        val repository = CachedFavoritesRepository(sessions, store, remote)

        assertEquals(
            FavoritesReadResult.Failed(FavoritesFailure.STORAGE),
            repository.observeFavorites().first(),
        )
        sessions.account.value = null
        assertEquals(FavoritesUpdateResult.Failed(FavoritesFailure.AUTH_REQUIRED), repository.refresh())
        assertEquals(0, remote.requests)
    }
}

private const val TIME = "2026-10-01T12:00:00.123456Z"

private fun favorite(id: String = "one") = FavoriteDto("reader", id, TIME, title = "server title")

private class FakeAccountAccess : AccountSessionAccess {
    override val account = MutableStateFlow<AccountIdentity?>(AccountIdentity("reader", 1))
    var refreshes = 0
    var rejected = false
    private var token = "old"

    override suspend fun credentials(identity: AccountIdentity, rejectedToken: String?): CredentialsResult {
        if (rejectedToken != null) {
            refreshes++
            token = "new"
        }

        return CredentialsResult.Ready(AccountCredentials(identity, token))
    }

    override suspend fun commitIfCurrent(identity: AccountIdentity, commit: suspend () -> Unit): Boolean {
        if (identity != account.value) return false

        commit()

        return true
    }

    override suspend fun reject(identity: AccountIdentity, accessToken: String) {
        rejected = true
    }
}

private class FakeFavoritesStore : FavoritesStore {
    private val accounts = mutableMapOf<String, MutableStateFlow<CachedFavorites>>()
    var failWrites = false
    var failReads = false

    override fun observe(userId: String) = flow {
        check(!failReads)
        state(userId).collect { emit(it) }
    }

    override suspend fun read(userId: String) = state(userId).value

    override suspend fun writePage(userId: String, page: FavoritesPage, replace: Boolean) {
        check(!failWrites)

        val entries = (if (replace) emptyList() else read(userId).entries) + page.entries

        state(userId).value = CachedFavorites(entries, page.next, true)
    }

    override suspend fun save(entry: FavoriteDto) {
        check(!failWrites)

        val cached = read(entry.userId)

        state(entry.userId).value =
            cached.copy(entries = cached.entries.filterNot { it.articleId == entry.articleId } + entry)
    }

    override suspend fun remove(userId: String, articleId: String) {
        check(!failWrites)

        state(userId).value =
            read(userId).let { it.copy(entries = it.entries.filterNot { row -> row.articleId == articleId }) }
    }

    private fun state(userId: String) = accounts.getOrPut(userId) { MutableStateFlow(CachedFavorites()) }
}

private class FakeFavoritesRemote : FavoritesRemoteSource {
    override suspend fun membership(credentials: AccountCredentials, articleIds: List<String>) = result(credentials) {
        page.entries.map { it.articleId }.filter { it in articleIds }.toSet()
    }

    var requests = 0
    var gate: CompletableDeferred<Unit>? = null
    var failure: FavoritesFailure? = null
    var page = FavoritesPage(listOf(favorite()), null)
    var lastCursor: FavoritesCursor? = null
    var rejectOld = false

    override suspend fun fetch(
        credentials: AccountCredentials,
        cursor: FavoritesCursor?,
    ): FavoritesResponse<FavoritesPage> {
        lastCursor = cursor

        return result(credentials) { page }
    }

    override suspend fun find(credentials: AccountCredentials, articleId: String) = result(credentials) {
        favorite(articleId)
    }

    override suspend fun add(credentials: AccountCredentials, article: Article) = result(credentials) { Unit }

    override suspend fun remove(credentials: AccountCredentials, articleId: String) = result(credentials) { Unit }

    private suspend fun <T> result(credentials: AccountCredentials, value: () -> T): FavoritesResponse<T> {
        requests++
        gate?.await()

        if (rejectOld && credentials.accessToken == "old") {
            return FavoritesResponse.Failed(FavoritesFailure.AUTH_REQUIRED)
        }

        return failure?.let { FavoritesResponse.Failed(it) } ?: FavoritesResponse.Success(value())
    }
}
