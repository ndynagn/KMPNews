package com.ndynagn.kmp.news.feature.search.di

import com.ndynagn.kmp.news.data.news.configureNewsHttpClient
import com.ndynagn.kmp.news.network.NewsApiConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/** Creates an app-owned search graph without opening storage. Cancel consumers before closing it once. */
fun createSearchDependencies(configuration: NewsApiConfiguration): SearchDependencies =
    assembleSearchDependencies(HttpClient(OkHttp) { configureNewsHttpClient(configuration) }, configuration)
