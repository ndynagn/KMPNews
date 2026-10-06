package com.ndynagn.kmp.news.feature.favorites.di

import com.ndynagn.kmp.news.feature.auth.di.AuthDependencies
import com.ndynagn.kmp.news.feature.favorites.data.CachedFavoritesRepository
import com.ndynagn.kmp.news.feature.favorites.data.FavoritesRemoteSource
import com.ndynagn.kmp.news.feature.favorites.data.SupabaseFavoritesClient
import com.ndynagn.kmp.news.feature.favorites.data.local.FavoritesDao
import com.ndynagn.kmp.news.feature.favorites.data.local.FavoritesDatabase
import com.ndynagn.kmp.news.feature.favorites.data.local.FavoritesStore
import com.ndynagn.kmp.news.feature.favorites.data.local.RoomFavoritesStore
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesRepository
import com.ndynagn.kmp.news.network.AccountSessionAccess
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import org.koin.core.KoinApplication
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/** App-owned favorites graph. Borrows Auth; close this graph before closing its Auth owner. */
class FavoritesDependencies internal constructor(
    private val graph: KoinApplication,
    private val database: FavoritesDatabase,
    private val client: HttpClient,
) {
    val favoritesRepository: FavoritesRepository = graph.koin.get()

    /** Cancel all callers/collectors first. Does not close Auth or erase persisted snapshots. Call once. */
    fun close() {
        try {
            graph.close()
        } finally {
            try {
                client.close()
            } finally {
                database.close()
            }
        }
    }
}

internal fun assembleFavoritesDependencies(
    auth: AuthDependencies,
    database: FavoritesDatabase,
    createClient: () -> HttpClient,
): FavoritesDependencies {
    var client: HttpClient? = null
    var graph: KoinApplication? = null

    try {
        val httpClient = createClient().also { client = it }
        val configuration = auth.configuration
        val application = koinApplication {
            graph = this
            modules(
                module {
                    single<AccountSessionAccess> { auth.accountAccess }
                    single<FavoritesDao> { database.favoritesDao() }
                    single<RoomFavoritesStore>() bind FavoritesStore::class
                    single<FavoritesRemoteSource> {
                        SupabaseFavoritesClient(
                            httpClient,
                            configuration.projectUrl,
                            configuration.publishableKey,
                            configuration.isConfigured,
                        )
                    }
                    single<CachedFavoritesRepository>() bind FavoritesRepository::class
                },
            )
        }

        return FavoritesDependencies(application, database, httpClient)
    } catch (failure: Throwable) {
        listOf<() -> Unit>({ graph?.close() }, { client?.close() }, { database.close() }).forEach { close ->
            try {
                close()
            } catch (cleanup: Throwable) {
                failure.addSuppressed(cleanup)
            }
        }
        throw failure
    }
}

internal fun HttpClientConfig<*>.configureFavoritesClient() {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }
    // No Logging plugin: access tokens and private favorites must never enter HTTP logs.
}
