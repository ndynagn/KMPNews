package com.ndynagn.kmp.news.feature.favorites

import com.ndynagn.kmp.news.feature.favorites.data.CachedFavoritesRepository
import com.ndynagn.kmp.news.feature.favorites.data.SupabaseFavoritesClient
import com.ndynagn.kmp.news.feature.favorites.data.local.RoomFavoritesStore
import com.ndynagn.kmp.news.feature.favorites.data.local.openFavoritesDatabase
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesReadResult
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesUpdateResult
import com.ndynagn.kmp.news.network.AccountCredentials
import com.ndynagn.kmp.news.network.AccountIdentity
import com.ndynagn.kmp.news.network.AccountSessionAccess
import com.ndynagn.kmp.news.network.CredentialsResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/** Synthetic transport and account; opt-in Apple verification source set only. */
class FavoritesInteropFixture(databasePath: String) {
    private val identity = AccountIdentity("fixture", 1)
    private val sessions = object : AccountSessionAccess {
        override val account = MutableStateFlow<AccountIdentity?>(identity)

        override suspend fun credentials(identity: AccountIdentity, rejectedToken: String?) =
            CredentialsResult.Ready(AccountCredentials(identity, "synthetic-token"))

        override suspend fun commitIfCurrent(identity: AccountIdentity, commit: suspend () -> Unit): Boolean {
            if (account.value != identity) return false
            commit()
            return true
        }

        override suspend fun reject(identity: AccountIdentity, accessToken: String) {
            account.value = null
        }
    }
    private val allowed = CompletableDeferred<Unit>()
    private val started = CompletableDeferred<Unit>()
    private val observers = MutableStateFlow(0)
    private val requests = MutableStateFlow(0)
    private val database = openFavoritesDatabase(databasePath)
    private val client = HttpClient(
        MockEngine {
            requests.update { it + 1 }
            started.complete(Unit)
            try {
                allowed.await()
                respond(
                    """[{"user_id":"fixture","article_id":"saved","added_at":"2026-10-01T00:00:00Z"}]""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } finally {
                requests.update { it - 1 }
            }
        },
    )
    private val repository = CachedFavoritesRepository(
        sessions,
        RoomFavoritesStore(database.favoritesDao()),
        SupabaseFavoritesClient(client, "https://fixture.supabase.co", "synthetic-key", true),
    )

    val activeObservers: Int get() = observers.value
    val activeRequests: Int get() = requests.value

    fun observeFavorites(): Flow<FavoritesReadResult> = repository.observeFavorites()
        .onStart { observers.update { it + 1 } }
        .onCompletion { observers.update { it - 1 } }

    suspend fun refresh(): FavoritesUpdateResult = repository.refresh()

    suspend fun awaitRequestStarted() = started.await()

    fun allowResponse() {
        allowed.complete(Unit)
    }

    fun signOut() {
        sessions.account.value = null
    }

    fun close() {
        client.close()
        database.close()
    }
}
