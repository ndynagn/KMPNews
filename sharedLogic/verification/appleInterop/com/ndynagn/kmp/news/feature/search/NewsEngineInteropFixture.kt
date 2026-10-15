package com.ndynagn.kmp.news.feature.search

import com.ndynagn.kmp.news.data.news.configureNewsHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

/** Opt-in actual Darwin engine consumer of the same local mediator contract as JVM/Android. */
class NewsEngineInteropFixture(private val port: Int) {
    suspend fun verify() {
        val client = HttpClient(Darwin) { configureNewsHttpClient(localNewsConfiguration) }

        verifyLocalNewsEngine(client, port)
    }
}
