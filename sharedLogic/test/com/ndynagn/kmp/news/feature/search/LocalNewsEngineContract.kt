package com.ndynagn.kmp.news.feature.search

import com.ndynagn.kmp.news.data.news.MediatedNewsClient
import com.ndynagn.kmp.news.data.news.NewsRequestFailure
import com.ndynagn.kmp.news.data.news.NewsResponse
import com.ndynagn.kmp.news.network.NewsApiConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** Actual mediator serialization and cancellation over a real engine; every request is forced to loopback. */
internal suspend fun verifyLocalNewsEngine(client: HttpClient, port: Int) {
    require(port in 1..65535)
    client.plugin(HttpSend).intercept { request ->
        request.url.protocol = URLProtocol.HTTP
        request.url.host = "127.0.0.1"
        request.url.port = port
        execute(request)
    }
    val remote = MediatedNewsClient(client, localNewsConfiguration)

    try {
        val success = remote.fetch("🌍 local", "opaque+cursor&one")

        check(success is NewsResponse.Success && success.page.articles.single().id == "engine-fixture")
        check(remote.fetch("invalid", null) == NewsResponse.Failure(NewsRequestFailure.INVALID_RESPONSE))
        check(remote.fetch("failure", null) == NewsResponse.Failure(NewsRequestFailure.SERVER_UNAVAILABLE))
        check(remote.fetch("disconnect", null) == NewsResponse.Failure(NewsRequestFailure.NETWORK))

        coroutineScope {
            val request = async { remote.fetch("cancel", null) }

            withTimeout(5_000) {
                while (client.get("started").bodyAsText() != "started") delay(10)
            }
            request.cancelAndJoin()

            check(request.isCancelled)
        }
    } finally {
        client.close()
        withTimeout(5_000) { client.coroutineContext[Job]?.join() }
    }
    check(client.coroutineContext[Job]?.isCompleted == true)
}

internal val localNewsConfiguration = NewsApiConfiguration("https://fixture.supabase.co", "sb_publishable_fixture")
