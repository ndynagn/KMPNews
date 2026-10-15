package com.ndynagn.kmp.news.data.news

import com.ndynagn.kmp.news.network.NewsApiConfiguration
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json

internal fun HttpClientConfig<*>.configureNewsHttpClient(configuration: NewsApiConfiguration) {
    expectSuccess = false
    followRedirects = false
    defaultRequest { if (configuration.isConfigured) url(configuration.functionsUrl) }
    install(ContentNegotiation) { json(newsJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }
}
