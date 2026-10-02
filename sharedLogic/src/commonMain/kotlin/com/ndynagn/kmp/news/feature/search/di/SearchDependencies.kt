package com.ndynagn.kmp.news.feature.search.di

import com.ndynagn.kmp.news.data.news.MediatedNewsClient
import com.ndynagn.kmp.news.feature.search.data.RemoteSearchRepository
import com.ndynagn.kmp.news.feature.search.domain.SearchRepository
import com.ndynagn.kmp.news.network.NewsApiConfiguration
import io.ktor.client.HttpClient
import org.koin.core.KoinApplication
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/** App-owned network-only graph. Share across windows; cancel consumers before closing once. */
class SearchDependencies internal constructor(
    private val application: KoinApplication,
    private val client: HttpClient,
) {
    val searchRepository: SearchRepository = application.koin.get()

    /** Closes both resources, preserving the first failure. Does not await caller-owned search tasks. */
    fun close() {
        var failure: Throwable? = null
        for (close in listOf(application::close, client::close)) {
            try {
                close()
            } catch (error: Throwable) {
                val first = failure
                if (first == null) failure = error else first.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}

internal fun assembleSearchDependencies(
    client: HttpClient,
    configuration: NewsApiConfiguration,
    onApplicationCreated: (KoinApplication) -> Unit = {},
): SearchDependencies {
    var acquired: KoinApplication? = null
    try {
        val application = koinApplication {
            acquired = this
            modules(
                module {
                    single<HttpClient> { client }
                    single<NewsApiConfiguration> { configuration }
                    single<MediatedNewsClient>()
                    single<RemoteSearchRepository>() bind SearchRepository::class
                },
            )
        }
        onApplicationCreated(application)
        return SearchDependencies(application, client)
    } catch (error: Throwable) {
        for (close in listOf({ acquired?.close() }, { client.close() })) {
            try {
                close()
            } catch (cleanup: Throwable) {
                error.addSuppressed(cleanup)
            }
        }
        throw error
    }
}
