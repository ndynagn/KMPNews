package com.ndynagn.kmp.news.feature.search

import com.ndynagn.kmp.news.data.news.configureNewsHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/** Opt-in Android engine consumer; adb reverse routes its loopback port to the local fixture server. */
class NewsEngineDeviceFixture(private val port: Int) {
    suspend fun verify() {
        val client = HttpClient(OkHttp) { configureNewsHttpClient(localNewsConfiguration) }

        verifyLocalNewsEngine(client, port)
    }
}
