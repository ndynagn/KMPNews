package com.ndynagn.kmp.news.feature.auth.di

import com.ndynagn.kmp.news.feature.auth.data.AuthClock
import com.ndynagn.kmp.news.feature.auth.data.AuthRemoteSource
import com.ndynagn.kmp.news.feature.auth.data.AuthSessionStorage
import com.ndynagn.kmp.news.feature.auth.data.PersistentAuthRepository
import com.ndynagn.kmp.news.feature.auth.data.SupabaseAuthClient
import com.ndynagn.kmp.news.feature.auth.domain.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import org.koin.core.KoinApplication
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single
import kotlin.time.Clock

/** One application-owned Auth graph, independent of feed storage and HTTP configuration. */
class AuthDependencies internal constructor(private val graph: KoinApplication, private val client: HttpClient) {
    val authRepository: AuthRepository = graph.koin.get()

    /** Call after all callers/observers stop; does not erase the saved session. */
    fun close() {
        graph.close()
        client.close()
    }
}

internal fun assembleAuthDependencies(
    configuration: AuthConfiguration,
    storage: AuthSessionStorage,
    client: HttpClient,
): AuthDependencies {
    val graph = koinApplication {
        modules(
            module {
                single<AuthClock> { AuthClock { Clock.System.now().epochSeconds } }
                single<AuthSessionStorage> { storage }
                single<AuthRemoteSource> { SupabaseAuthClient(client, configuration) }
                single<PersistentAuthRepository>() bind AuthRepository::class
            },
        )
    }
    return AuthDependencies(graph, client)
}

internal fun HttpClientConfig<*>.configureAuthClient() {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }
    // Deliberately no HTTP Logging plugin: Auth bodies and headers contain credentials.
}
