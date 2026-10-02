package com.ndynagn.kmp.news.feature.search

import com.ndynagn.kmp.news.data.news.configureNewsHttpClient
import com.ndynagn.kmp.news.feature.search.di.assembleSearchDependencies
import com.ndynagn.kmp.news.network.NewsApiConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred

/** Synthetic opt-in bridge fixture. Never included in a normal application framework. */
class SearchInteropFixture(shouldFail: Boolean) {
    private val started = CompletableDeferred<Unit>()
    private val allowed = CompletableDeferred<Unit>()
    private val finished = CompletableDeferred<Unit>()
    private val configuration = NewsApiConfiguration("https://fixture.supabase.co", "sb_publishable_fixture")
    private val dependencies = assembleSearchDependencies(
        HttpClient(
            MockEngine {
                started.complete(Unit)
                try {
                    allowed.await()
                    if (shouldFail) {
                        respond(
                            """{"status":"error","results":{"code":"ApiLimitExceeded"}}""",
                            HttpStatusCode.TooManyRequests,
                            headersOf("Content-Type", "application/json"),
                        )
                    } else {
                        respond(
                            """{"status":"success","totalResults":1,"results":[{"article_id":"fixture"}]}""",
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    }
                } finally {
                    finished.complete(Unit)
                }
            },
        ) { configureNewsHttpClient(configuration) },
        configuration,
    )
    val repository = dependencies.searchRepository
    fun allowResponse() {
        allowed.complete(Unit)
    }
    suspend fun awaitStarted() {
        started.await()
    }
    suspend fun awaitFinished() {
        finished.await()
    }
    fun close() {
        dependencies.close()
    }
}
