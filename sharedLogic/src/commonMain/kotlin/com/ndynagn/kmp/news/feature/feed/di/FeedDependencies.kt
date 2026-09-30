package com.ndynagn.kmp.news.feature.feed.di

import com.ndynagn.kmp.news.feature.feed.data.OfflineFirstNewsRepository
import com.ndynagn.kmp.news.feature.feed.data.local.FeedDatabase
import com.ndynagn.kmp.news.feature.feed.data.local.FeedStore
import com.ndynagn.kmp.news.feature.feed.data.local.RoomFeedStore
import com.ndynagn.kmp.news.feature.feed.data.remote.NewsDataClient
import com.ndynagn.kmp.news.feature.feed.data.remote.NewsRemoteSource
import com.ndynagn.kmp.news.feature.feed.data.remote.feedJson
import com.ndynagn.kmp.news.feature.feed.domain.FeedClock
import com.ndynagn.kmp.news.feature.feed.domain.NewsRepository
import com.ndynagn.kmp.news.feature.feed.domain.RefreshFeedIfNeeded
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import org.koin.core.KoinApplication
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single
import kotlin.time.Clock

/**
 * Owns one isolated graph and its resources; share it across consumers of one database.
 *
 * Cancel all consumer tasks before calling [close] once. See the repository's
 * [feed contract](../../../../../../../../../../../docs/news-feed-data-domain-plan.md), Public API and resource ownership.
 */
class FeedDependencies internal constructor(
    private val application: KoinApplication,
    private val database: FeedDatabase,
    private val httpClient: HttpClient,
) {
    val newsRepository: NewsRepository = application.koin.get()
    val refreshFeedIfNeeded: RefreshFeedIfNeeded = application.koin.get()

    /**
     * Attempts to close Koin, the HTTP client and the database in that order.
     *
     * Does not await coroutine completion or affect other owners. The first cleanup
     * failure is rethrown after all attempts, with later failures suppressed.
     * Concurrent/repeated closure is not supported; no Swift error bridge is provided.
     */
    fun close() {
        closeFeedResources(null, application::close, httpClient::close, database::close)
    }
}

/** The internal callback allows failure checks after container acquisition without changing public factories. */
internal fun assembleFeedDependencies(
    database: FeedDatabase,
    httpClient: HttpClient,
    apiKey: String,
    onApplicationCreated: (KoinApplication) -> Unit = {},
): FeedDependencies {
    var acquiredApplication: KoinApplication? = null

    try {
        val application = koinApplication {
            // Retain ownership even if module registration fails before this call returns.
            acquiredApplication = this
            modules(
                module {
                    single<FeedClock> { FeedClock { Clock.System.now().toEpochMilliseconds() } }
                    single<FeedStore> { RoomFeedStore(database.feedDao()) }
                    single<NewsRemoteSource> { NewsDataClient(httpClient, apiKey) }
                    single<OfflineFirstNewsRepository>() bind NewsRepository::class
                    factory<RefreshFeedIfNeeded>()
                },
            )
        }

        onApplicationCreated(application)
        return FeedDependencies(application, database, httpClient)
    } catch (failure: Throwable) {
        closeFeedResources(failure, { acquiredApplication?.close() }, httpClient::close, database::close)
        throw failure
    }
}

/** Transfers both resources to assembly, or closes the database if client creation fails. */
internal fun createFeedDependenciesWithClient(
    database: FeedDatabase,
    apiKey: String,
    createHttpClient: () -> HttpClient,
): FeedDependencies {
    val httpClient = try {
        createHttpClient()
    } catch (failure: Throwable) {
        closeFeedResources(failure, database::close)
        throw failure
    }

    return assembleFeedDependencies(database, httpClient, apiKey)
}

internal fun HttpClientConfig<*>.configureFeedHttpClient(httpLogger: FeedHttpLogger? = null) {
    expectSuccess = false
    followRedirects = false

    defaultRequest {
        url("https://newsdata.io/api/")
    }

    if (httpLogger != null) {
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) = httpLogger.log(message)
            }
            level = LogLevel.HEADERS
            filter { request ->
                request.url.protocol == URLProtocol.HTTPS && request.url.host == "newsdata.io"
            }
            sanitizeHeader { name ->
                name.lowercase() in setOf(
                    "x-access-key",
                    "authorization",
                    "proxy-authorization",
                    "cookie",
                    "set-cookie",
                )
            }
        }
    }

    install(ContentNegotiation) {
        json(feedJson)
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }
}
